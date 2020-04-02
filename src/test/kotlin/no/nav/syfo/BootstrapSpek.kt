package no.nav.syfo

import java.io.StringReader
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object BootstrapSpek : Spek({
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val sykmelding2013 = extractHelseOpplysningerArbeidsuforhet(fellesformat)
    describe("Testing extract sykmeld2013") {
        it("Returns internal server error when liveness check fails") {
            sykmelding2013.regelSettVersjon shouldEqual "2"
        }
    }
})
