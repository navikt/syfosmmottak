package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object ArsakKodeMissingSpek : Spek({

    describe("Validate arsakskodeIsmissing is false") {
        it("Validate MedisinskeArsaker Arsakskode is mapped") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/generated_sm_8.xml").readAllBytes().toString(Charsets.UTF_8))) as HelseOpplysningerArbeidsuforhet

            arsakskodeIsmissing(healthInformation) shouldEqual false
        }
        it("Validate arsakskodeIsmissing is false") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/generated_sm.xml").readAllBytes().toString(Charsets.UTF_8))) as HelseOpplysningerArbeidsuforhet

            arsakskodeIsmissing(healthInformation) shouldEqual false
        }

        it("Validate arsakskodeIsmissing is true") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/generated_sm_9.xml").readAllBytes().toString(Charsets.UTF_8))) as HelseOpplysningerArbeidsuforhet

            arsakskodeIsmissing(healthInformation) shouldEqual true
        }

        it("Validate arsakskodeIsmissing is true") {
            val aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

            aktivitetIkkeMuligMissingArsakskode(aktivitetIkkeMulig) shouldEqual false
        }
    }
})