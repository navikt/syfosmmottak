package no.nav.syfo

import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import no.nav.syfo.handlestatus.opprettProduceTask
import no.nav.syfo.handlestatus.pilotBehandleneEnhet
import no.nav.syfo.handlestatus.sendToSyfosmManuell
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HandleStatusManualProcessingSpek : Spek({
    val loggingMeta = LoggingMeta("", "", "")

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

        it("Should return true when the only rule hit is ruleName is TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE and behandleneEnhet is 0417 in prod") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                    RuleInfo(ruleName = "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE",
                            messageForUser = "Første sykmelding er tilbakedatert og årsak for tilbakedatering er angitt.",
                            messageForSender = "Første sykmelding er tilbakedatert og felt 11.2 (begrunnelseIkkeKontakt) er utfylt",
                            ruleStatus = Status.MANUAL_PROCESSING
                    )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0415", "prod-fss", LocalDate.of(2020, 12, 10)) shouldEqualTo true
        }

        it("Should return false when the only rule hit is ruleName is PASIENTEN_HAR_KODE_6 and behandleneEnhet is 0417 in prod") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                    RuleInfo(ruleName = "PASIENTEN_HAR_KODE_6",
                            messageForUser = "Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig",
                            messageForSender = "Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig",
                            ruleStatus = Status.MANUAL_PROCESSING
                    )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0417", "prod-fss", LocalDate.of(2021, 1, 10)) shouldEqualTo false
        }
        it("Should return false when rulehits contain SYKMELDING_MED_BEHANDLINGSDAGER and behandlendeEnhet is 0417 in prod") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                RuleInfo(ruleName = "SYKMELDING_MED_BEHANDLINGSDAGER",
                    messageForUser = "Sykmelding inneholder behandlingsdager.",
                    messageForSender = "Sykmelding inneholder behandlingsdager.",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0417", "prod-fss", LocalDate.of(2021, 1, 10)) shouldEqualTo false
        }
        it("Should return false when rulehits contain SYKMELDING_MED_REISETILSKUDD and behandlendeEnhet is 0417 in prod") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                RuleInfo(ruleName = "SYKMELDING_MED_REISETILSKUDD",
                    messageForUser = "Sykmelding inneholder reisetilskudd.",
                    messageForSender = "Sykmelding inneholder reisetilskudd.",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0417", "prod-fss", LocalDate.of(2021, 1, 10)) shouldEqualTo false
        }
        it("Skal ikke sende til manuell i dev hvis bruker har kode 6") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                RuleInfo(ruleName = "PASIENTEN_HAR_KODE_6",
                    messageForUser = "Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig",
                    messageForSender = "Pasient er registrert med sperrekode 6, sperret adresse, strengt fortrolig",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0417", "dev-fss", LocalDate.of(2020, 12, 10)) shouldEqualTo false
        }
        it("Skal sende tilbakedatering til manuell i dev uavhengig av pilotkontor") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                RuleInfo(ruleName = "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE",
                    messageForUser = "Første sykmelding er tilbakedatert og årsak for tilbakedatering er angitt.",
                    messageForSender = "Første sykmelding er tilbakedatert og felt 11.2 (begrunnelseIkkeKontakt) er utfylt",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0301", "dev-fss", LocalDate.of(2020, 12, 10)) shouldEqualTo true
        }
        it("Skal sende tilbakedatering til manuell i prod for ikke-pilot 1/1 2021") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                RuleInfo(ruleName = "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE",
                    messageForUser = "Første sykmelding er tilbakedatert og årsak for tilbakedatering er angitt.",
                    messageForSender = "Første sykmelding er tilbakedatert og felt 11.2 (begrunnelseIkkeKontakt) er utfylt",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0301", "prod-fss", LocalDate.of(2021, 1, 1)) shouldEqualTo true
        }
        it("Skal ikke sende tilbakedatering til manuell i prod for ikke-pilot før 1/1 2021") {
            val validationResult = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                RuleInfo(ruleName = "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE",
                    messageForUser = "Første sykmelding er tilbakedatert og årsak for tilbakedatering er angitt.",
                    messageForSender = "Første sykmelding er tilbakedatert og felt 11.2 (begrunnelseIkkeKontakt) er utfylt",
                    ruleStatus = Status.MANUAL_PROCESSING
                )
            ))

            sendToSyfosmManuell(validationResult.ruleHits, "0301", "prod-fss", LocalDate.of(2020, 12, 31)) shouldEqualTo false
        }
    }

    describe("Oppretter manuelle oppgaver med riktige parametre") {
        val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
        it("Behandlingstema er ANY hvis sykmelding ikke har behandlingsdager eller reisetilskudd") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("PASIENTEN_HAR_KODE_6", "kode6", "kode6", Status.MANUAL_PROCESSING)))

            runBlocking {
                val oppgave = opprettProduceTask(receivedSykmelding, validationResults, loggingMeta)

                oppgave.behandlingstema shouldEqual "ANY"
            }
        }
        it("Behandlingstema er ab0351 hvis sykmelding har behandlingsdager") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("SYKMELDING_MED_BEHANDLINGSDAGER", "Sykmelding inneholder behandlingsdager.", "Sykmelding inneholder behandlingsdager.", Status.MANUAL_PROCESSING)))

            runBlocking {
                val oppgave = opprettProduceTask(receivedSykmelding, validationResults, loggingMeta)

                oppgave.behandlingstema shouldEqual "ab0351"
            }
        }
        it("Behandlingstema er ab0237 hvis sykmelding har reisetilskudd") {
            val validationResults = ValidationResult(Status.MANUAL_PROCESSING, listOf(RuleInfo("SYKMELDING_MED_REISETILSKUDD", "Sykmelding inneholder reisetilskudd.", "Sykmelding inneholder reisetilskudd.", Status.MANUAL_PROCESSING)))

            runBlocking {
                val oppgave = opprettProduceTask(receivedSykmelding, validationResults, loggingMeta)

                oppgave.behandlingstema shouldEqual "ab0237"
            }
        }
    }
})
