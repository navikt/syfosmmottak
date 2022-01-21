package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.vedlegg.model.Content
import no.nav.syfo.vedlegg.model.Vedlegg
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

class ExtractVedleggDocumentsKtTest : Spek({
    describe("Sykmelding med vedlegg") {
        it("skal filtrere bort vedlegg") {
            val inputMessageText = no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            removeVedleggFromFellesformat(fellesformat)

            fellesformat.get<XMLMsgHead>().document.size shouldBeEqualTo 1
        }

        it("Skal hente liste med vedlegg") {
            val inputMessageText = no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            val vedlegg = getVedlegg(fellesformat)

            vedlegg.size shouldBeEqualTo 1
            vedlegg.first() shouldBeEqualTo Vedlegg(
                Content("Base64Container", "base64"),
                "application/pdf",
                "vedlegg.pdf"
            )
        }

        it("Skal hente 2 vedlegg") {
            val inputMessageText =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat_with_2_vedlegg.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            val vedlegg = getVedlegg(fellesformat)

            vedlegg.size shouldBeEqualTo 2
            vedlegg.first() shouldBeEqualTo Vedlegg(
                Content("Base64Container", "base64"),
                "application/pdf",
                "vedlegg.pdf"
            )
            vedlegg[1] shouldBeEqualTo Vedlegg(Content("Base64Container", "base64"), "application/pdf", "vedlegg2.pdf")
        }
    }
})
