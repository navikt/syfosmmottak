package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.log
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.TEST_FNR_IN_PROD
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendReceivedSykmelding
import no.nav.syfo.sendValidationResult
import no.nav.syfo.service.updateRedis
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import redis.clients.jedis.Jedis

fun handleStatusINVALID(
    validationResult: ValidationResult,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013InvalidHandlingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    sm2013ApprecTopic: String,
    sm2013BehandlingsUtfallToipic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead
) {
    sendValidationResult(validationResult, kafkaproducervalidationResult, sm2013BehandlingsUtfallToipic, receivedSykmelding, loggingMeta)
    sendReceivedSykmelding(sm2013InvalidHandlingTopic, receivedSykmelding, kafkaproducerreceivedSykmelding)
    val apprec = fellesformat.toApprec(
        ediLoggId,
        msgId,
        msgHead,
        ApprecStatus.AVVIST,
        null,
        msgHead.msgInfo.receiver.organisation,
        msgHead.msgInfo.sender.organisation,
        msgHead.msgInfo.genDate,
        validationResult
    )
    sendReceipt(apprec, sm2013ApprecTopic, kafkaproducerApprec)
}

fun handleDuplicateSM2013Content(
    redisSha256String: String,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    log.warn(
        "Melding med {} har samme innhold som tidligere mottatt sykmelding og er avvist som duplikat {}",
        keyValue("originalEdiLoggId", redisSha256String),
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen er avvist fordi den er " +
            "identisk med en allerede mottatt sykmelding (duplikat)," +
            " og den kan derfor ikke sendes på nytt. Pasienten har ikke fått beskjed. " +
            "Kontakt din EPJ-leverandør hvis dette ikke stemmer",
        ediLoggId, msgId, msgHead
    )

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handleDuplicateEdiloggid(
    redisEdiloggid: String,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    log.warn(
        "Melding med {} har samme ediLoggId som tidligere mottatt sykmelding og er avvist som duplikat {}",
        keyValue("originalEdiLoggId", redisEdiloggid),
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Denne sykmeldingen har ein identisk identifikator med ein sykmelding som er mottatt tidligere, og er derfor ein duplikat." +
            "og skal ikke sendes på nytt. Dersom dette ikke stemmer, kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handlePatientNotFoundInPDL(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi pasienten ikke finnes i folkeregisteret {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Pasienten er ikke registrert i folkeregisteret",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleDoctorNotFoundInPDL(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi legen ikke finnes i folkeregisteret {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Behandler er ikke registrert i folkeregisteret",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleAktivitetOrPeriodeIsMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi det ikke er oppgitt noen sykmeldingsperioder {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Ingen perioder er oppgitt i sykmeldingen.",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleBiDiagnoserDiagnosekodeIsMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi bidiagnoser er angitt, men mangler diagnosekode (v) {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Diagnosekode på bidiagnose mangler",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleBiDiagnoserDiagnosekodeVerkIsMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi bidiagnoser er angitt, men mangler diagnosekodeverk (s) {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Diagnosekodeverk på bidiagnose mangler. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleBiDiagnoserDiagnosekodeBeskrivelseMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi bidiagnoser er angitt, men mangler diagnosekodebeskrivelse (dn) {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Diagnosekode beskrivelse på bidiagnose mangler. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleFnrAndDnrAndHprIsmissingFromBehandler(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi den mangler både fnr/dnr og HPR-nummer for behandler {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Fødselsnummer/d-nummer/Hpr-nummer på behandler mangler",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleHovedDiagnoseDiagnosekodeMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi hoveddiagnose mangler diagnosekode (v) {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Diagnosekode for hoveddiagnose mangler i sykmeldingen. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleHovedDiagnoseDiagnoseBeskrivelseMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi hoveddiagnose mangler diagnosekodebeskrivelse (dn) {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "Diagnosekode beskrivelse for hoveddiagnose mangler i sykmeldingen. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleMedisinskeArsakskodeIsmissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi medisinsk årsak er angitt, men årsakskode (v) mangler {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "MedisinskeArsaker Arsakskode V mangler i sykmeldingen. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleMedisinskeArsakskodeHarUgyldigVerdi(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi medisinsk årsak er angitt, men årsakskode (v) har ugyldig verdi {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "MedisinskeArsaker Arsakskode V i sykmeldingen har ugyldig verdi. Gyldige verdier er 1,2,3,9. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleArbeidsplassenArsakskodeIsmissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi arbeidsplassen er angitt som årsak, men årsakskode (v) mangler {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "ArbeidsplassenArsaker Arsakskode V mangler i sykmeldingen. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleArbeidsplassenArsakskodeHarUgyldigVerdi(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi arbeidsplassen er angitt som årsak, men årsakskode (v) har ugyldig verdi {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "ArbeidsplassenArsaker Arsakskode V i sykmeldingen har ugyldig verdi. Gyldige verdier er 1 og 9. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

fun handleTestFnrInProd(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmelding avvist: Testfødselsnummer er kommet inn i produksjon! {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Testfødselsnummer er kommet inn i produksjon, " +
            "dette er eit alvorlig brudd som aldri burde oppstå. Kontakt din EPJ-leverandør snarest",
        ediLoggId, msgId, msgHead
    )

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    TEST_FNR_IN_PROD.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleAnnenFraversArsakkodeVIsmissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    jedis: Jedis,
    sha256String: String
) {
    log.warn(
        "Sykmeldingen er avvist fordi annen fraværsårsak er angitt, men årsakskode (v) mangler {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName)
    )

    val apprec = fellesformatToAppprec(
        fellesformat,
        "Sykmeldingen kan ikke rettes, det må skrives en ny." +
            "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
            "AnnenFravers Arsakskode V mangler i sykmeldingen. Kontakt din EPJ-leverandør",
        ediLoggId, msgId, msgHead
    )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, jedis, ediLoggId, sha256String)
}

private fun sendApprec(
    apprec: Apprec,
    env: Environment,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    loggingMeta: LoggingMeta,
    jedis: Jedis,
    ediLoggId: String,
    sha256String: String
) {
    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun fellesformatToAppprec(
    fellesformat: XMLEIFellesformat,
    tekstTilSykmelder: String,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead
): Apprec =
    fellesformat.toApprec(
        ediLoggId,
        msgId,
        msgHead,
        ApprecStatus.AVVIST,
        tekstTilSykmelder,
        msgHead.msgInfo.receiver.organisation,
        msgHead.msgInfo.sender.organisation,
        msgHead.msgInfo.genDate
    )
