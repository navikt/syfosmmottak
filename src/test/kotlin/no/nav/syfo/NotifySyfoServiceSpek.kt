package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object NotifySyfoServiceSpek : Spek({
    describe("Legger sykmelding på kø til syfoservice") {
        it("Sykmeldingen Base64-encodes på ISO-8859-1 format") {
            val sm = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/helseopplysninger-ISO-8859-1.xml").readAllBytes().toString(Charsets.ISO_8859_1))) as HelseOpplysningerArbeidsuforhet

            val sykemeldingsBytes = convertSykemeldingToBase64(sm)

            val sykmelding = String(sykemeldingsBytes, Charsets.ISO_8859_1)
            sykmelding.contains("Bodø") shouldEqual true
        }
    }
})
