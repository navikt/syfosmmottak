package no.nav.syfo.handlestatus

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
import no.nav.syfo.sendReceivedSykmelding
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import javax.jms.MessageProducer
import javax.jms.Session

fun handleStatusOK(
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    apprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    loggingMeta: LoggingMeta,
    session: Session,
    syfoserviceProducer: MessageProducer,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    syfoserviceQueueName: String,
    okSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>
) {

    sendReceivedSykmelding(okSykmeldingTopic, receivedSykmelding, kafkaproducerreceivedSykmelding)

    val apprec = fellesformat.toApprec(
        ediLoggId,
        msgId,
        msgHead,
        ApprecStatus.OK,
        null,
        msgHead.msgInfo.receiver.organisation,
        msgHead.msgInfo.sender.organisation,
        msgHead.msgInfo.genDate
    )
    sendReceipt(apprec, apprecTopic, kafkaproducerApprec)

    notifySyfoService(
        session = session, receiptProducer = syfoserviceProducer, ediLoggId = ediLoggId,
        sykmeldingId = receivedSykmelding.sykmelding.id, msgId = msgId, healthInformation = healthInformation
    )

    log.info("Message send to syfoService {}, {}", syfoserviceQueueName, StructuredArguments.fields(loggingMeta))
}
