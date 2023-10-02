package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.duplicationcheck.model.Duplicate
import no.nav.syfo.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.logger
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.SYKMELDING_AVVIST_DUPLIKCATE_COUNTER
import no.nav.syfo.metrics.SYKMELDING_AVVIST_VIRUS_VEDLEGG_COUNTER
import no.nav.syfo.metrics.TEST_FNR_IN_PROD
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendReceivedSykmelding
import no.nav.syfo.sendValidationResult
import no.nav.syfo.service.DuplicationService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.get
import org.apache.kafka.clients.producer.KafkaProducer

fun handleStatusINVALID(
    validationResult: ValidationResult,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    avvistSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    apprecTopic: String,
    behandlingsUtfallTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
) {
    sendValidationResult(
        validationResult,
        kafkaproducervalidationResult,
        behandlingsUtfallTopic,
        receivedSykmelding,
        loggingMeta
    )
    sendReceivedSykmelding(
        avvistSykmeldingTopic,
        receivedSykmelding,
        kafkaproducerreceivedSykmelding
    )
    val apprec =
        fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.AVVIST,
            null,
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation,
            msgHead.msgInfo.genDate,
            validationResult,
            fellesformat.get<XMLMottakenhetBlokk>().ebService,
        )
    sendReceipt(apprec, apprecTopic, kafkaproducerApprec, loggingMeta)
}

fun handleDuplicateSM2013Content(
    originalEdiLoggId: String,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicate: Duplicate,
) {
    logger.warn(
        "Melding med {} har samme innhold som tidligere mottatt sykmelding og er avvist som duplikat {} {}",
        keyValue("originalEdiLoggId", originalEdiLoggId),
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen er avvist fordi den er " +
                "identisk med en allerede mottatt sykmelding (duplikat)," +
                " og den kan derfor ikke sendes på nytt. Pasienten har ikke fått beskjed. " +
                "Kontakt din EPJ-leverandør hvis dette ikke stemmer",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendReceipt(apprec, env.apprecTopic, kafkaproducerApprec, loggingMeta)
    logger.info("Apprec receipt sent to kafka topic {}, {}", env.apprecTopic, fields(loggingMeta))
    duplicationService.persistDuplication(duplicate)
    INVALID_MESSAGE_NO_NOTICE.inc()
    SYKMELDING_AVVIST_DUPLIKCATE_COUNTER.inc()
}

fun handlePatientNotFoundInPDL(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi pasienten ikke finnes i folkeregisteret {}, {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Pasienten er ikke registrert i folkeregisteret",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleDoctorNotFoundInPDL(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi legen ikke finnes i folkeregisteret {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Behandler er ikke registrert i folkeregisteret",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleAktivitetOrPeriodeIsMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi det ikke er oppgitt noen sykmeldingsperioder {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Ingen perioder er oppgitt i sykmeldingen.",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handlePeriodetypeMangler(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi det ikke er oppgitt noen type for en eller flere sykmeldingsperioder {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Sykmeldingen inneholder en eller flere perioder uten type (100%, gradert, reisetilskudd, avventende eller behandlingsdager).",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleBiDiagnoserDiagnosekodeIsMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi bidiagnoser er angitt, men mangler diagnosekode (v) {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Diagnosekode på bidiagnose mangler",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleBiDiagnoserDiagnosekodeVerkIsMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi bidiagnoser er angitt, men mangler diagnosekodeverk (s) {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Diagnosekodeverk på bidiagnose mangler. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleBiDiagnoserDiagnosekodeBeskrivelseMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi bidiagnoser er angitt, men mangler diagnosekodebeskrivelse (dn) {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Diagnosekode beskrivelse på bidiagnose mangler. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleFnrAndDnrAndHprIsmissingFromBehandler(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi den mangler både fnr/dnr og HPR-nummer for behandler {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Fødselsnummer/d-nummer/Hpr-nummer på behandler mangler",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleHovedDiagnoseDiagnosekodeMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi hoveddiagnose mangler diagnosekode (v) {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Diagnosekode for hoveddiagnose mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleHovedDiagnoseDiagnoseBeskrivelseMissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi hoveddiagnose mangler diagnosekodebeskrivelse (dn) {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Diagnosekode beskrivelse for hoveddiagnose mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleMedisinskeArsakskodeIsmissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi medisinsk årsak er angitt, men årsakskode (v) mangler {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "MedisinskeArsaker Arsakskode V mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleMedisinskeArsakskodeHarUgyldigVerdi(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi medisinsk årsak er angitt, men årsakskode (v) har ugyldig verdi {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "MedisinskeArsaker Arsakskode V i sykmeldingen har ugyldig verdi. Gyldige verdier er 1,2,3,9. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleArbeidsplassenArsakskodeIsmissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi arbeidsplassen er angitt som årsak, men årsakskode (v) mangler {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "ArbeidsplassenArsaker Arsakskode V mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleArbeidsplassenArsakskodeHarUgyldigVerdi(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi arbeidsplassen er angitt som årsak, men årsakskode (v) har ugyldig verdi {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "ArbeidsplassenArsaker Arsakskode V i sykmeldingen har ugyldig verdi. Gyldige verdier er 1 og 9. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleTestFnrInProd(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmelding avvist: Testfødselsnummer er kommet inn i produksjon! {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Testfødselsnummer er kommet inn i produksjon, " +
                "dette er eit alvorlig brudd som aldri burde oppstå. Kontakt din EPJ-leverandør snarest",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendReceipt(apprec, env.apprecTopic, kafkaproducerApprec, loggingMeta)
    logger.info("Apprec receipt sent to kafka topic {}, {}", env.apprecTopic, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    TEST_FNR_IN_PROD.inc()
    duplicationService.persistDuplicationCheck(duplicateCheck)
}

fun handleAnnenFraversArsakkodeVIsmissing(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi annen fraværsårsak er angitt, men årsakskode (v) mangler {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "AnnenFravers Arsakskode V mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleVirksomhetssykmeldingOgHprMangler(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Virksomhetsykmeldingen er avvist fordi den mangler HPR-nummer for behandler {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "HPR-nummer for juridisk behandler mangler",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleVirksomhetssykmeldingOgFnrManglerIHPR(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Virksomhetsykmeldingen er avvist fordi fødselsnummer mangler i HPR for behandler {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Fødselsnummer for juridisk behandler mangler i HPR",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(
        apprec,
        env,
        kafkaproducerApprec,
        loggingMeta,
        duplicationService,
        duplicateCheck,
    )
}

fun handleBehandletDatoMangler(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi kontaktMedPasient behandletDato mangler {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "BehandletDato felt 12.1 mangler",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(
        apprec,
        env,
        kafkaproducerApprec,
        loggingMeta,
        duplicationService,
        duplicateCheck,
    )
}

fun handleVedleggContainsVirus(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmelding er avvist fordi eit eller flere vedlegg kan potensielt inneholde virus {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Eit eller flere vedlegg kan potensielt inneholde virus",
            ediLoggId,
            msgId,
            msgHead,
        )

    SYKMELDING_AVVIST_VIRUS_VEDLEGG_COUNTER.inc()

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

fun handleSignaturDatoInTheFuture(
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    logger.warn(
        "Sykmeldingen er avvist fordi signaturdatoen(GenDate) er frem i tid {} {}",
        fields(loggingMeta),
        keyValue("avvistAv", env.applicationName),
    )

    val apprec =
        fellesformatToAppprec(
            fellesformat,
            "Sykmeldingen kan ikke rettes, det må skrives en ny." +
                "Pasienten har ikke fått beskjed, men venter på ny sykmelding fra deg. Grunnet følgende:" +
                "Signaturdatoen(GenDate) er frem i tid. Kontakt din EPJ-leverandør",
            ediLoggId,
            msgId,
            msgHead,
        )

    sendApprec(apprec, env, kafkaproducerApprec, loggingMeta, duplicationService, duplicateCheck)
}

private fun sendApprec(
    apprec: Apprec,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    loggingMeta: LoggingMeta,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
) {
    sendReceipt(apprec, env.apprecTopic, kafkaproducerApprec, loggingMeta)
    logger.info("Apprec receipt sent to kafka topic {}, {}", env.apprecTopic, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    duplicationService.persistDuplicationCheck(duplicateCheck)
}

fun fellesformatToAppprec(
    fellesformat: XMLEIFellesformat,
    tekstTilSykmelder: String,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
): Apprec =
    fellesformat.toApprec(
        ediLoggId,
        msgId,
        msgHead,
        ApprecStatus.AVVIST,
        tekstTilSykmelder,
        msgHead.msgInfo.receiver.organisation,
        msgHead.msgInfo.sender.organisation,
        msgHead.msgInfo.genDate,
        null,
        fellesformat.get<XMLMottakenhetBlokk>().ebService,
    )
