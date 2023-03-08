package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.StringReader

internal class ExtractHelseOpplysningerArbeisuforehetTest {

    @Test
    internal fun `Testing extracting data from HelseOpplysningerArbeidsuforhet extract tlf from behandler`() {
        val stringInput =
            no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
        val kontaktInfo = extractHelseOpplysningerArbeidsuforhet(fellesformat).behandler.kontaktInfo

        val tlfFraBehnandler = extractTlfFromKontaktInfo(kontaktInfo)

        Assertions.assertEquals("12345678", tlfFraBehnandler)
    }

    @Test
    internal fun `Testing extracting data from HelseOpplysningerArbeidsuforhet extract tlf from pasient`() {
        val stringInput =
            no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
        val kontaktInfo = extractHelseOpplysningerArbeidsuforhet(fellesformat).pasient.kontaktInfo

        val tlfFraPasient = extractTlfFromKontaktInfo(kontaktInfo)

        Assertions.assertEquals("mob:12345678", tlfFraPasient)
    }

    @Test
    internal fun `Testing extracting data from HelseOpplysningerArbeidsuforhet extract hpr`() {
        val stringInput =
            no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
        val hpr = padHpr(extractHpr(fellesformat)?.id?.trim())

        Assertions.assertEquals("123456789", hpr)
    }
}
