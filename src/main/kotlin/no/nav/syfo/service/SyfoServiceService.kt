package no.nav.syfo.service

import java.util.Base64
import javax.jms.MessageProducer
import javax.jms.Session
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.convertSykemeldingToBase64
import no.nav.syfo.model.Syfo
import no.nav.syfo.model.Tilleggsdata
import no.nav.syfo.util.extractSyketilfelleStartDato
import no.nav.syfo.util.xmlObjectWriter

fun notifySyfoService(
    session: Session,
    receiptProducer: MessageProducer,
    ediLoggId: String,
    sykmeldingId: String,
    msgId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet

) {
    receiptProducer.send(session.createTextMessage().apply {

        val syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation)
        val sykmelding = convertSykemeldingToBase64(healthInformation)
        val syfo = Syfo(
                tilleggsdata = Tilleggsdata(ediLoggId = ediLoggId, sykmeldingId = sykmeldingId, msgId = msgId, syketilfelleStartDato = syketilfelleStartDato),
                sykmelding = Base64.getEncoder().encodeToString(sykmelding))
        text = xmlObjectWriter.writeValueAsString(syfo)
    })
}
