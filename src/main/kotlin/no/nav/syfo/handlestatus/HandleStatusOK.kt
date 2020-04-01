package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.bootstrap.KafkaClients
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.sendReceipt
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.ProducerRecord

fun handleStatusOK(
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    sm2013ApprecTopic: String,
    loggingMeta: LoggingMeta,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    syfoserviceQueueName: String,
    sm2013AutomaticHandlingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    kafkaClients: KafkaClients
) {

    kafkaClients.kafkaProducerReceivedSykmelding.send(ProducerRecord(sm2013AutomaticHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
    log.info("Message send to kafka {}, {}", sm2013AutomaticHandlingTopic, StructuredArguments.fields(loggingMeta))

    kafkaClients.syfoserviceKafkaProducer.publishSykmeldingToKafka(sykmeldingId = receivedSykmelding.sykmelding.id, helseOpplysningerArbeidsuforhet = healthInformation)
    log.info("Message send to syfoService {}, {}", syfoserviceQueueName, StructuredArguments.fields(loggingMeta))

    val apprec = toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.OK,
            null,
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation
    )
    sendReceipt(apprec, sm2013ApprecTopic, kafkaClients.kafkaProducerApprec)
    log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopic, StructuredArguments.fields(loggingMeta))
}
