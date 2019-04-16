package no.nav.syfo

import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TaskDescriptionSpek : Spek({
    describe("Notify syfo service payload") {
        it("Produces a parsable XML") {
            val results = ValidationResult(status = Status.MANUAL_PROCESSING, ruleHits = listOf(
                    RuleInfo(ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                            messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                            messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)"
                            ),
                    RuleInfo(ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                            messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                            messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling."
                    )
                    ))
            // val beskrivelse = "Manuell behandling sykmelding: ${results.ruleHits.joinToString{ it.messageForSender }}"

            val beskrivelse = "Manuell behandling sykmelding: ${results.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"

            println(beskrivelse)
        }
    }
})