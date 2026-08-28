package no.nav.syfo.util

import java.io.StringReader
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.utils.getFileAsString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EncodingUtilTest {

    private val original = getFileAsString("src/test/resources/fellesformat-valid-encoding.xml")
    private val doubleEncoded =
        getFileAsString("src/test/resources/fellesformat-invalid-encoding.xml")

    @Test
    internal fun `Invalid testfile is the valid testfile with UTF-8 bytes decoded as ISO-8859-1`() {
        assertEquals(
            String(original.toByteArray(Charsets.UTF_8), Charsets.ISO_8859_1),
            doubleEncoded,
        )
    }

    @Test
    internal fun `Repairs message where UTF-8 bytes were decoded as ISO-8859-1`() {
        assertTrue(doubleEncoded.contains("FÃ¸dselsnummer"))

        val fixed = fixDoubleEncodedUtf8(doubleEncoded)

        assertEquals(original, fixed)
    }

    @Test
    internal fun `Leaves correctly encoded message unchanged`() {
        assertSame(original, fixDoubleEncodedUtf8(original))
    }

    @Test
    internal fun `Leaves pure ASCII message unchanged`() {
        val ascii = "<?xml version=\"1.0\"?><EI_fellesformat/>"
        assertSame(ascii, fixDoubleEncodedUtf8(ascii))
    }

    @Test
    internal fun `Repaired message unmarshals with correct norwegian characters`() {
        val fixed = fixDoubleEncodedUtf8(doubleEncoded)

        val fellesformat =
            fellesformatUnmarshaller.unmarshal(StringReader(fixed)) as XMLEIFellesformat
        val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat).first

        assertEquals("Øøø Æææ", healthInformation.pasient.navnFastlege)
        assertEquals("Bærum Sykehjem", healthInformation.arbeidsgiver.navnArbeidsgiver)
    }
}
