package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult

class TaskDescriptionSpek : FunSpec({
    context("Notify syfo service payload") {
        test("Produces a parsable XML") {
            val results = ValidationResult(
                status = Status.MANUAL_PROCESSING,
                ruleHits = listOf(
                    RuleInfo(
                        ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                        messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                        messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)",
                        ruleStatus = Status.MANUAL_PROCESSING
                    ),
                    RuleInfo(
                        ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                        messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                        messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                        ruleStatus = Status.MANUAL_PROCESSING
                    )
                )
            )

            val beskrivelse = "Manuell behandling sykmelding: ${results.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"

            println(beskrivelse)
        }
    }
})
