package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.fnrOgDnrMangler
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object BehandlerMissingFNRDNRSpek : Spek({
    describe("Should check find if fnr or dnr is missing") {
        it("FNR is Not missing") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm.xml"))) as HelseOpplysningerArbeidsuforhet

            fnrOgDnrMangler(healthInformation) shouldBeEqualTo false
        }

        it("FNR AND DNR is missing") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_6.xml"))) as HelseOpplysningerArbeidsuforhet

            fnrOgDnrMangler(healthInformation) shouldBeEqualTo true
        }

        it("FNR is empty string") {
            val healthInformation =
                fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_7.xml"))) as HelseOpplysningerArbeidsuforhet

            fnrOgDnrMangler(healthInformation) shouldBeEqualTo true
        }
    }
})
