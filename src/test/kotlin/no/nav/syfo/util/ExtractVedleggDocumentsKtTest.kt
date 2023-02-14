package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.vedlegg.model.Content
import no.nav.syfo.vedlegg.model.Vedlegg
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.StringReader

internal class ExtractVedleggDocumentsKtTest {

    @Test
    internal fun `Skal filtrere bort vedlegg`() {
        val inputMessageText = no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
        removeVedleggFromFellesformat(fellesformat)

        Assertions.assertEquals(1, fellesformat.get<XMLMsgHead>().document.size)
    }

    @Test
    internal fun `Skal hente liste med vedlegg`() {
        val inputMessageText = no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
        val vedlegg = getVedlegg(fellesformat)

        Assertions.assertEquals(1, vedlegg.size)
        Assertions.assertEquals(
            Vedlegg(
                Content("Base64Container", "base64"),
                "application/pdf",
                "vedlegg.pdf"
            ),
            vedlegg.first()
        )
    }

    @Test
    internal fun `Skal hente 2 vedlegg`() {
        val inputMessageText =
            no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat_with_2_vedlegg.xml")
        val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
        val vedlegg = getVedlegg(fellesformat)

        Assertions.assertEquals(2, vedlegg.size)
        Assertions.assertEquals(
            Vedlegg(
                Content("Base64Container", "base64"),
                "application/pdf",
                "vedlegg.pdf"
            ),
            vedlegg.first()
        )

        Assertions.assertEquals(
            Vedlegg(Content("Base64Container", "base64"), "application/pdf", "vedlegg2.pdf"),
            vedlegg[1]
        )
    }
}
