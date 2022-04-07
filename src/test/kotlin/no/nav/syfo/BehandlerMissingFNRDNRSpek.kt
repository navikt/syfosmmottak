package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.fnrOgDnrMangler
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import java.io.StringReader

class BehandlerMissingFNRDNRSpek : FunSpec({
    context("Should check find if fnr or dnr is missing") {
        test("FNR is Not missing") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm.xml"))) as HelseOpplysningerArbeidsuforhet

            fnrOgDnrMangler(healthInformation) shouldBeEqualTo false
        }

        test("FNR AND DNR is missing") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_6.xml"))) as HelseOpplysningerArbeidsuforhet

            fnrOgDnrMangler(healthInformation) shouldBeEqualTo true
        }

        test("FNR is empty string") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_7.xml"))) as HelseOpplysningerArbeidsuforhet

            fnrOgDnrMangler(healthInformation) shouldBeEqualTo true
        }
    }
})
