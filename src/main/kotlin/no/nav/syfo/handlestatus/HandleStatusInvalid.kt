package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments
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
import no.nav.syfo.model.IdentInfoResult
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendValidationResult
import no.nav.syfo.service.updateRedis
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
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

    kafkaproducerreceivedSykmelding.send(ProducerRecord(sm2013InvalidHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
    log.info("Message send to kafka {}, {}", sm2013InvalidHandlingTopic, fields(loggingMeta))

    val apprec = fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.AVVIST,
            null,
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation,
            validationResult
    )
    sendReceipt(apprec, sm2013ApprecTopic, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopic, fields(loggingMeta))
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
    log.warn("Message with {} marked as duplicate, has same redisSha256String {}",
            keyValue("originalEdiLoggId", redisSha256String), fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
            "Skal ikke sendes på nytt",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, StructuredArguments.fields(loggingMeta))
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
    log.warn("Message with {} marked as duplicate, has same redisEdiloggid {}", StructuredArguments.keyValue("originalEdiLoggId", redisEdiloggid), StructuredArguments.fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
            "Skal ikke sendes på nytt",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, StructuredArguments.fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
}

fun handlePatientNotFoundInAktorRegister(
    patientIdents: IdentInfoResult?,
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
    log.info("Patient not found i aktorRegister error: {}, {}",
            keyValue("errorMessage", patientIdents?.feilmelding ?: "No response for FNR"),
            fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Pasienten er ikkje registrert i folkeregisteret",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, StructuredArguments.fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleDoctorNotFoundInAktorRegister(
    doctorIdents: IdentInfoResult?,
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
    log.info("Doctor not found i aktorRegister error: {}, {}",
            keyValue("errorMessage", doctorIdents?.feilmelding ?: "No response for FNR"),
            fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Behandler er ikkje registrert i folkeregisteret",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
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
    log.info("Periode is missing {}", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Ingen perioder er oppgitt i sykmeldingen.",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
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
    log.info("diagnosekode is missing {}", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Diagnosekode på bidiagnose mangler",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
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
    log.info("Diagnosekodeverk S is missing {}", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Diagnosekodeverk på bidiagnose mangler. Kontakt din EPJ-leverandør",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
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
    log.info("Diagnosekodebeskrivelse DN is missing {}", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Diagnosekode beskrivelse på bidiagnose mangler. Kontakt din EPJ-leverandør",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleFnrAndDnrIsmissingFromBehandler(
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
    log.info("FNR or DNR is missing on behandler {}", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Fødselsnummer/d-nummer på behandler mangler",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleHouvedDiagnoseDiagnosekodeMissing(
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
    log.info("Houveddiagnose diagnosekode V mangler", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Diagnosekode for hoveddiagnose mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}

fun handleHouvedDiagnoseDiagnoseBeskrivelseMissing(
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
    log.info("Houveddiagnose diagnosekode beskrivelse DN mangler", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Diagnosekode beskrivelse for hoveddiagnose mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
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
    log.info("MedisinskeArsaker Arsakskode V mangler", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "MedisinskeArsaker Arsakskode V mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
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
    log.info("Arbeidsplassen Arsakskode V mangler", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "ArbeidsplassenArsaker Arsakskode V mangler i sykmeldingen. Kontakt din EPJ-leverandør",
            ediLoggId, msgId, msgHead)

    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
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
    log.info("Test fødselsnummer er kommet inn i produksjon", fields(loggingMeta))

    val apprec = fellesformatToAppprec(fellesformat, "Test fødselsnummer er kommet inn i produksjon, dette er ikkje lov. Kontakt din EPJ-leverandør",
            ediLoggId, msgId, msgHead)

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
                msgHead.msgInfo.sender.organisation
        )
