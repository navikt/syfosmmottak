package no.nav.syfo.handlestatus

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toReceivedSykmeldingWithValidation
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.get
import org.apache.kafka.clients.producer.KafkaProducer

fun handleStatusOK(
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    apprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    loggingMeta: LoggingMeta,
    okSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmeldingWithValidation>,
) {
    sendReceivedSykmelding(
        okSykmeldingTopic,
        receivedSykmelding.toReceivedSykmeldingWithValidation(
            ValidationResult(status = Status.OK, ruleHits = emptyList())
        ),
        kafkaproducerreceivedSykmelding,
    )

    val apprec =
        fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.OK,
            null,
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation,
            msgHead.msgInfo.genDate,
            null,
            fellesformat.get<XMLMottakenhetBlokk>().ebService,
        )
    sendReceipt(apprec, apprecTopic, kafkaproducerApprec, loggingMeta)
}
