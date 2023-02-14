package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.fnrOgDnrMangler
import no.nav.syfo.utils.getFileAsString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.StringReader

internal class BehandlerMissingFNRDNRTest {
    @Test
    internal fun `Should check find if fnr or dnr is missing FNR is Not missing`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm.xml"))) as HelseOpplysningerArbeidsuforhet

        Assertions.assertEquals(false, fnrOgDnrMangler(healthInformation))
    }

    @Test
    internal fun `Should check find if fnr or dnr is missing FNR AND DNR is missing`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_6.xml"))) as HelseOpplysningerArbeidsuforhet

        Assertions.assertEquals(true, fnrOgDnrMangler(healthInformation))
    }

    @Test
    internal fun `Should check find if fnr or dnr is missing FNR is empty string`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_7.xml"))) as HelseOpplysningerArbeidsuforhet

        Assertions.assertEquals(true, fnrOgDnrMangler(healthInformation))
    }
}
