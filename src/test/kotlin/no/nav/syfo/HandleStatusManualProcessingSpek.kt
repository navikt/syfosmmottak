package no.nav.syfo

import io.mockk.mockk
import no.nav.syfo.handlestatus.opprettProduceTask
import no.nav.syfo.handlestatus.sendToSyfosmManuell
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object HandleStatusManualProcessingSpek : Spek({
    val loggingMeta = LoggingMeta("", "", "")

    describe("Sending til syfosmmanuell") {
        it("Should return true when the only rule hit is ruleName is TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE") {
            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE",
                        messageForUser = "Første sykmelding er tilbakedatert og årsak for tilbakedatering er angitt.",
                        messageForSender = "Første sykmelding er tilbakedatert og felt 11.2 (begrunnelseIkkeKontakt) er utfylt",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            sendToSyfosmManuell(validationResult.ruleHits) shouldBeEqualTo true
        }
        it("Should return false when rulehits contain SYKMELDING_MED_BEHANDLINGSDAGER") {
            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "SYKMELDING_MED_BEHANDLINGSDAGER",
                        messageForUser = "Sykmelding inneholder behandlingsdager.",
                        messageForSender = "Sykmelding inneholder behandlingsdager.",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            sendToSyfosmManuell(validationResult.ruleHits) shouldBeEqualTo false
        }
        it("Should return false when rulehits contain SYKMELDING_MED_REISETILSKUDD") {
            val validationResult = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "SYKMELDING_MED_REISETILSKUDD",
                        messageForUser = "Sykmelding inneholder reisetilskudd.",
                        messageForSender = "Sykmelding inneholder reisetilskudd.",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            sendToSyfosmManuell(validationResult.ruleHits) shouldBeEqualTo false
        }
    }

    describe("Oppretter manuelle oppgaver med riktige parametre") {
        val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
        it("Behandlingstema er ab0351 hvis sykmelding har behandlingsdager") {
            val validationResults = ValidationResult(
                Status.MANUAL_PROCESSING,
                listOf(
                    RuleInfo(
                        "SYKMELDING_MED_BEHANDLINGSDAGER",
                        "Sykmelding inneholder behandlingsdager.",
                        "Sykmelding inneholder behandlingsdager.",
                        Status.MANUAL_PROCESSING
                    )
                )
            )

            val oppgave = opprettProduceTask(receivedSykmelding, validationResults, loggingMeta)

            oppgave.behandlingstema shouldBeEqualTo "ab0351"
        }
        it("Behandlingstema er ab0237 hvis sykmelding har reisetilskudd") {
            val validationResults = ValidationResult(
                Status.MANUAL_PROCESSING,
                listOf(
                    RuleInfo(
                        "SYKMELDING_MED_REISETILSKUDD",
                        "Sykmelding inneholder reisetilskudd.",
                        "Sykmelding inneholder reisetilskudd.",
                        Status.MANUAL_PROCESSING
                    )
                )
            )

            val oppgave = opprettProduceTask(receivedSykmelding, validationResults, loggingMeta)

            oppgave.behandlingstema shouldBeEqualTo "ab0237"
        }
    }
})
