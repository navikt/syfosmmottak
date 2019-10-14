package no.nav.syfo.handlestatus

import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.sendReceipt
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

fun handleStatusOK(
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    sm2013ApprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    loggingMeta: LoggingMeta,
    session: Session,
    syfoserviceProducer: MessageProducer,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    syfoserviceQueueName: String,
    sm2013AutomaticHandlingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>
) {

    kafkaproducerreceivedSykmelding.send(ProducerRecord(sm2013AutomaticHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
    log.info("Message send to kafka {}, {}", sm2013AutomaticHandlingTopic, StructuredArguments.fields(loggingMeta))

    notifySyfoService(session = session, receiptProducer = syfoserviceProducer, ediLoggId = ediLoggId,
            sykmeldingId = receivedSykmelding.sykmelding.id, msgId = msgId, healthInformation = healthInformation)
    log.info("Message send to syfoService {}, {}", syfoserviceQueueName, StructuredArguments.fields(loggingMeta))

    val apprec = fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.OK,
            null,
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation
    )
    sendReceipt(apprec, sm2013ApprecTopic, kafkaproducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopic, StructuredArguments.fields(loggingMeta))
}
