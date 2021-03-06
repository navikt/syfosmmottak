package no.nav.syfo

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.model.Syfo
import no.nav.syfo.model.Tilleggsdata
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.xmlObjectWriter
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader
import java.time.LocalDateTime
import java.util.Base64

object BootstrapSpek : Spek({
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val sykmelding2013 = extractHelseOpplysningerArbeidsuforhet(fellesformat)
    describe("Testing extract sykmeld2013") {
        it("Returns internal server error when liveness check fails") {
            sykmelding2013.regelSettVersjon shouldBeEqualTo "2"
        }
    }

    describe("Notify syfo service payload") {
        it("Produces a parsable XML") {
            val syfo = Syfo(
                tilleggsdata = Tilleggsdata(ediLoggId = "abc", msgId = "def", sykmeldingId = "sykmeldingId", syketilfelleStartDato = LocalDateTime.now()),
                sykmelding = Base64.getEncoder().encodeToString("LOL2k".toByteArray(Charsets.UTF_8))
            )
            val text = xmlObjectWriter.writeValueAsString(syfo)
            println(text)
        }
    }
})
