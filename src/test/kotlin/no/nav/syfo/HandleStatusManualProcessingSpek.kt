package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import no.nav.syfo.handlestatus.opprettOpprettOppgaveKafkaMessage
import no.nav.syfo.handlestatus.sendToSyfosmManuell
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo

class HandleStatusManualProcessingSpek : FunSpec({
    val loggingMeta = LoggingMeta("", "", "")

    context("Sending til syfosmmanuell") {
        test("Should return true when the only rule hit is ruleName is TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE") {
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
        test("Should return false when rulehits contain SYKMELDING_MED_BEHANDLINGSDAGER") {
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
    }

    context("Oppretter manuelle oppgaver med riktige parametre") {
        val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
        test("Behandlingstema er ab0351 hvis sykmelding har behandlingsdager") {
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

            val oppgave = opprettOpprettOppgaveKafkaMessage(receivedSykmelding, validationResults, loggingMeta)

            oppgave.behandlingstema shouldBeEqualTo "ab0351"
        }
    }
})
