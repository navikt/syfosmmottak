package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments
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
    log.info("Message send to kafka {}, {}", sm2013InvalidHandlingTopic, StructuredArguments.fields(loggingMeta))

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
    log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopic, StructuredArguments.fields(loggingMeta))
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
            StructuredArguments.keyValue("originalEdiLoggId", redisSha256String), StructuredArguments.fields(loggingMeta))

    val apprec = fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.AVVIST,
            "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                    "Skal ikke sendes på nytt",
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation
    )

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
    val apprec = fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.AVVIST,
            "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                    "Skal ikke sendes på nytt",
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation
    )

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
            StructuredArguments.keyValue("errorMessage", patientIdents?.feilmelding ?: "No response for FNR"),
            StructuredArguments.fields(loggingMeta))

    val apprec = fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.AVVIST,
            "Pasienten er ikkje registrert i folkeregisteret",
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation
    )
    sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, StructuredArguments.fields(loggingMeta))
    INVALID_MESSAGE_NO_NOTICE.inc()
    updateRedis(jedis, ediLoggId, sha256String)
}
