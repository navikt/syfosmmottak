package no.nav.syfo

import java.io.StringReader
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.aktivitetIkkeMuligMissingArbeidsplassenArsakskode
import no.nav.syfo.util.aktivitetIkkeMuligMissingMedisinskeArsakskode
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.medisinskeArsakskodeIsmissing
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ArsakKodeMissingSpek : Spek({

    describe("Validate arsakskodeIsmissing is false") {
        it("Validate Medisinske qArsaker Arsakskode is mapped") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_8.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeIsmissing(healthInformation) shouldEqual false
        }
        it("Validate arsakskodeIsmissing is false") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeIsmissing(healthInformation) shouldEqual false
        }

        it("Validate arsakskodeIsmissing is true") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_9.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeIsmissing(healthInformation) shouldEqual true
        }

        it("Validate medisinskearsakskodeIsmissing is true") {
            val aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

            aktivitetIkkeMuligMissingMedisinskeArsakskode(aktivitetIkkeMulig) shouldEqual false
        }

        it("Validate arbeidsplassenarsakskodeIsmissing is true") {
            val aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

            aktivitetIkkeMuligMissingArbeidsplassenArsakskode(aktivitetIkkeMulig) shouldEqual false
        }
    }
})
