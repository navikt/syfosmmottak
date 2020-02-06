package no.nav.syfo

import no.nav.syfo.handlestatus.pilotBehandleneEnhet
import org.amshove.kluent.shouldEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HandleStatusManualProcessingSpek : Spek({

    describe("Test the class HandleStatusManualProcessing and its methods") {
        it("Should return true when the behandleneEnhet is 0415") {
            pilotBehandleneEnhet("0415") shouldEqualTo true
        }
        it("Should return true when the behandleneEnhet is 0412") {
            pilotBehandleneEnhet("0412") shouldEqualTo true
        }
        it("Should return true when the behandleneEnhet is 0403") {
            pilotBehandleneEnhet("0403") shouldEqualTo true
        }
        it("Should return true when the behandleneEnhet is 0417") {
            pilotBehandleneEnhet("0417") shouldEqualTo true
        }
    }
})
