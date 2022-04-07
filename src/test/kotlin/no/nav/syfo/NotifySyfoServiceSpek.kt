package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.service.convertSykemeldingToBase64
import no.nav.syfo.util.fellesformatUnmarshaller
import org.amshove.kluent.shouldBeEqualTo
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

class NotifySyfoServiceSpek : FunSpec({
    context("Legger sykmelding på kø til syfoservice") {
        test("Sykmeldingen Base64-encodes på UTF-8 format") {
            val sm = fellesformatUnmarshaller.unmarshal(
                StringReader(
                    String(
                        Files.readAllBytes(Paths.get(("src/test/resources/helseopplysninger-UTF-8.xml"))),
                        StandardCharsets.UTF_8
                    )
                )
            ) as HelseOpplysningerArbeidsuforhet

            val sykemeldingsBytes = convertSykemeldingToBase64(sm)

            val sykmelding = String(sykemeldingsBytes, Charsets.UTF_8)
            sykmelding.contains("Bodø") shouldBeEqualTo true
        }
    }
})
