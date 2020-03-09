package no.nav.syfo

import no.nav.syfo.handlestatus.pilotBehandleneEnhet
import no.nav.syfo.handlestatus.sendToSyfosmManuell
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import org.amshove.kluent.shouldEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HandleStatusManualProcessingSpek : Spek({

    describe("Test the methods in HandleStatusManualProcessing") {
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

        it("Should return false when the behandleneEnhet is 0301") {
            pilotBehandleneEnhet("0301") shouldEqualTo false
        }

        it("Should return true when the only rule hit is ruleName is TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE and behandleneEnhet is 0417") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                    RuleInfo(ruleName = "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE",
                            messageForUser = "Første sykmelding er tilbakedatert og årsak for tilbakedatering er angitt.",
                            messageForSender = "Første sykmelding er tilbakedatert og felt 11.2 (begrunnelseIkkeKontakt) er utfylt",
                            ruleStatus = Status.MANUAL_PROCESSING
                    )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0415") shouldEqualTo true
        }

        it("Should return false when the only rule hit is ruleName is PASIENTEN_HAR_KODE_6 and behandleneEnhet is 0417") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                    RuleInfo(ruleName = "PASIENTEN_HAR_KODE_6",
                            messageForUser = "Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig",
                            messageForSender = "Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig",
                            ruleStatus = Status.MANUAL_PROCESSING
                    )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0417") shouldEqualTo false
        }
    }
})
