package no.nav.syfo.util

import java.io.StringReader
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.kafka.vedlegg.model.Content
import no.nav.syfo.kafka.vedlegg.model.Vedlegg
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class ExtractVedleggDocumentsKtTest : Spek({
    describe("Sykmelding med vedlegg") {
        it("skal filtrere bort vedlegg") {
            val inputMessageText = no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            removeVedleggFromFellesformat(fellesformat)

            fellesformat.get<XMLMsgHead>().document.size shouldEqual 1
        }

        it("Skal hente liste med vedlegg") {
            val inputMessageText = no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            val vedlegg = getVedlegg(fellesformat)

            vedlegg.size shouldEqual 1
            vedlegg.first() shouldEqual Vedlegg(Content("Base64Container", "base64"), "application/pdf", "vedlegg.pdf")
        }

        it("Skal hente 2 vedlegg") {
            val inputMessageText = no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat_with_2_vedlegg.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            val vedlegg = getVedlegg(fellesformat)

            vedlegg.size shouldEqual 2
            vedlegg.first() shouldEqual Vedlegg(Content("Base64Container", "base64"), "application/pdf", "vedlegg.pdf")
            vedlegg[1] shouldEqual Vedlegg(Content("Base64Container", "base64"), "application/pdf", "vedlegg2.pdf")
        }
    }
})
