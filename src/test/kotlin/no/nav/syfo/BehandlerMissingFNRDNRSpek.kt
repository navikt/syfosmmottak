package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object BehandlerMissingFNRDNRSpek : Spek({
    describe("Should check find if fnr or dnr is missing") {
        it("FNR is Not missing") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/generated_sm.xml").readAllBytes().toString(Charsets.UTF_8))) as HelseOpplysningerArbeidsuforhet

            fnrAndDnrIsmissingFromBehandler(healthInformation) shouldEqual false
        }

        it("FNR AND DNR is missing") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/generated_sm_6.xml").readAllBytes().toString(Charsets.UTF_8))) as HelseOpplysningerArbeidsuforhet

            fnrAndDnrIsmissingFromBehandler(healthInformation) shouldEqual true
        }

        it("FNR is empty string") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/generated_sm_7.xml").readAllBytes().toString(Charsets.UTF_8))) as HelseOpplysningerArbeidsuforhet

            fnrAndDnrIsmissingFromBehandler(healthInformation) shouldEqual true
        }
    }
})
