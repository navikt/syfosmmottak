package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import java.io.StringReader

class ExtractSykmeldingTest : FunSpec({
    context("Testing extract sykmeld2013") {
        test("Regelsettversjon 2") {
            val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
            val sykmelding2013 = extractHelseOpplysningerArbeidsuforhet(fellesformat)

            sykmelding2013.regelSettVersjon shouldBeEqualTo "2"
        }
        test("Regelsettversjon 3") {
            val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon3.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
            val sykmelding2013 = extractHelseOpplysningerArbeidsuforhet(fellesformat)

            sykmelding2013.regelSettVersjon shouldBeEqualTo "3"
        }
    }
})
