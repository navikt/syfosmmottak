package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.aktivitetIkkeMuligArbeidsplassenArsakskodeMangler
import no.nav.syfo.util.aktivitetIkkeMuligMedisinskeArsakskodeMangler
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.medisinskeArsakskodeHarUgyldigVerdi
import no.nav.syfo.util.medisinskeArsakskodeMangler
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import java.io.StringReader

class ArsakKodeMissingSpek : FunSpec({

    context("Validate arsakskodeIsmissing is false") {
        test("Validate Medisinske Arsaker Arsakskode is mapped") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_8.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeMangler(healthInformation) shouldBeEqualTo false
        }
        test("Validate arsakskodeIsmissing is false") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeMangler(healthInformation) shouldBeEqualTo false
        }

        test("Validate arsakskodeIsmissing is true") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_9.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeMangler(healthInformation) shouldBeEqualTo true
        }

        test("Validate medisinskearsakskodeIsmissing is true") {
            val aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

            aktivitetIkkeMuligMedisinskeArsakskodeMangler(aktivitetIkkeMulig) shouldBeEqualTo false
        }

        test("Validate arbeidsplassenarsakskodeIsmissing is true") {
            val aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

            aktivitetIkkeMuligArbeidsplassenArsakskodeMangler(aktivitetIkkeMulig) shouldBeEqualTo false
        }
        test("medisinskeArsakskodeHarUgyldigVerdi er false hvis V=1") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_8.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeHarUgyldigVerdi(healthInformation) shouldBeEqualTo false
        }
        test("medisinskeArsakskodeHarUgyldigVerdi er true hvis V=A") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_10.xml"))) as HelseOpplysningerArbeidsuforhet

            medisinskeArsakskodeHarUgyldigVerdi(healthInformation) shouldBeEqualTo true
        }
    }
})
