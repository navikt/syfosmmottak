package no.nav.syfo

import io.mockk.mockk
import no.nav.syfo.handlestatus.opprettOpprettOppgaveKafkaMessage
import no.nav.syfo.handlestatus.sendToSyfosmManuell
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class HandleStatusManualProcessingTest {
    val loggingMeta = LoggingMeta("", "", "")

    @Test
    internal fun `Should return true when the only rule hit is ruleName is TILBAKEDATERT_MER_ENN_8_DAGER_FORSTE_SYKMELDING_MED_BEGRUNNELSE`() {
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

        Assertions.assertEquals(true, sendToSyfosmManuell(validationResult.ruleHits))
    }

    @Test
    internal fun `Should return false when rulehits contain SYKMELDING_MED_BEHANDLINGSDAGER`() {
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

        Assertions.assertEquals(false, sendToSyfosmManuell(validationResult.ruleHits))
    }

    @Test
    internal fun `Behandlingstema er ab0351 hvis sykmelding har behandlingsdager`() {
        val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
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

        Assertions.assertEquals("ab0351", oppgave.behandlingstema)
    }
}
