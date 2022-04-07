package no.nav.syfo.util

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.vedlegg.model.Content
import no.nav.syfo.vedlegg.model.Vedlegg
import org.amshove.kluent.shouldBeEqualTo
import java.io.StringReader

class ExtractVedleggDocumentsKtTest : FunSpec({
    context("Sykmelding med vedlegg") {
        test("skal filtrere bort vedlegg") {
            val inputMessageText = no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            removeVedleggFromFellesformat(fellesformat)

            fellesformat.get<XMLMsgHead>().document.size shouldBeEqualTo 1
        }

        test("Skal hente liste med vedlegg") {
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

        test("Skal hente 2 vedlegg") {
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
