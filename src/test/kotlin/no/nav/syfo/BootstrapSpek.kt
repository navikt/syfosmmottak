package no.nav.syfo

import no.nav.syfo.utils.getFileAsString
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

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
