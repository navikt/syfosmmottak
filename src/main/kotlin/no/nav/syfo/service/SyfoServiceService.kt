package no.nav.syfo.service

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.Syfo
import no.nav.syfo.model.Tilleggsdata
import no.nav.syfo.util.extractSyketilfelleStartDato
import no.nav.syfo.util.sykmeldingMarshaller
import no.nav.syfo.util.xmlObjectWriter
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.jms.MessageProducer
import javax.jms.Session

fun notifySyfoService(
    session: Session,
    receiptProducer: MessageProducer,
    ediLoggId: String,
    sykmeldingId: String,
    msgId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet

) {
    receiptProducer.send(
        session.createTextMessage().apply {

            val syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation)
            val sykmelding = convertSykemeldingToBase64(healthInformation)
            val syfo = Syfo(
                tilleggsdata = Tilleggsdata(ediLoggId = ediLoggId, sykmeldingId = sykmeldingId, msgId = msgId, syketilfelleStartDato = syketilfelleStartDato),
                sykmelding = Base64.getEncoder().encodeToString(sykmelding)
            )
            text = xmlObjectWriter.writeValueAsString(syfo)
        }
    )
}

fun convertSykemeldingToBase64(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): ByteArray =
    ByteArrayOutputStream().use {
        sykmeldingMarshaller.marshal(helseOpplysningerArbeidsuforhet, it)
        it
    }.toByteArray()
