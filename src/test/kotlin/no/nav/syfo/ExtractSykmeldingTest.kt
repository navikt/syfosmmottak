package no.nav.syfo

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.StringReader

internal class ExtractSykmeldingTest {

    @Test
    internal fun `Testing extract sykmeld2013 regelsettversjon 2`() {
        val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
        val sykmelding2013 = extractHelseOpplysningerArbeidsuforhet(fellesformat)

        Assertions.assertEquals("2", sykmelding2013.regelSettVersjon)
    }

    @Test
    internal fun `Testing extract sykmeld2013 regelsettversjon 3`() {
        val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon3.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
        val sykmelding2013 = extractHelseOpplysningerArbeidsuforhet(fellesformat)

        Assertions.assertEquals("3", sykmelding2013.regelSettVersjon)
    }
}
