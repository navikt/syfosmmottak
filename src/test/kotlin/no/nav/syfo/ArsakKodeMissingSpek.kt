package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.aktivitetIkkeMuligArbeidsplassenArsakskodeMangler
import no.nav.syfo.util.aktivitetIkkeMuligMedisinskeArsakskodeMangler
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.medisinskeArsakskodeHarUgyldigVerdi
import no.nav.syfo.util.medisinskeArsakskodeMangler
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object ArsakKodeMissingSpek : Spek({

    describe("Validate arsakskodeIsmissing is false") {
        it("Validate Medisinske Arsaker Arsakskode is mapped") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_8.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeMangler(healthInformation) shouldBeEqualTo false
        }
        it("Validate arsakskodeIsmissing is false") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeMangler(healthInformation) shouldBeEqualTo false
        }

        it("Validate arsakskodeIsmissing is true") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_9.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeMangler(healthInformation) shouldBeEqualTo true
        }

        it("Validate medisinskearsakskodeIsmissing is true") {
            val aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

            aktivitetIkkeMuligMedisinskeArsakskodeMangler(aktivitetIkkeMulig) shouldBeEqualTo false
        }

        it("Validate arbeidsplassenarsakskodeIsmissing is true") {
            val aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

            aktivitetIkkeMuligArbeidsplassenArsakskodeMangler(aktivitetIkkeMulig) shouldBeEqualTo false
        }
        it("medisinskeArsakskodeHarUgyldigVerdi er false hvis V=1") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_8.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeHarUgyldigVerdi(healthInformation) shouldBeEqualTo false
        }
        it("medisinskeArsakskodeHarUgyldigVerdi er true hvis V=A") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_10.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeHarUgyldigVerdi(healthInformation) shouldBeEqualTo true
        }
    }
})
