package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object NotifySyfoServiceSpek : Spek({
    describe("Testing converting sykemdlign to Base64") {
        it("Produces a parsable XML") {
            val sm = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/generated_sm.xml").readAllBytes().toString(Charsets.UTF_8))) as HelseOpplysningerArbeidsuforhet

            val sykemeldingsBytes = convertSykemeldingToBase64(sm)

            println(objectMapper.writeValueAsString(sykemeldingsBytes))
        }
    }
})