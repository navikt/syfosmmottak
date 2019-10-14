package no.nav.syfo

import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.service.convertSykemeldingToBase64
import no.nav.syfo.util.fellesformatUnmarshaller
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object NotifySyfoServiceSpek : Spek({
    describe("Legger sykmelding på kø til syfoservice") {
        it("Sykmeldingen Base64-encodes på ISO-8859-1 format") {
            val sm = fellesformatUnmarshaller.unmarshal(StringReader(String(Files.readAllBytes(Paths.get(("src/test/resources/helseopplysninger-ISO-8859-1.xml"))), StandardCharsets.ISO_8859_1))) as HelseOpplysningerArbeidsuforhet

            val sykemeldingsBytes = convertSykemeldingToBase64(sm)

            val sykmelding = String(sykemeldingsBytes, Charsets.ISO_8859_1)
            sykmelding.contains("Bodø") shouldEqual true
        }
    }
})
