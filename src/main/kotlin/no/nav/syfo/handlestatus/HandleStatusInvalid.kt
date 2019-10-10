package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendValidationResult
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

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