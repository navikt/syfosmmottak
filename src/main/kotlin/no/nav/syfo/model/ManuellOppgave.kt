package no.nav.syfo.model

import no.nav.syfo.apprec.Apprec

data class ManuellOppgave(
        val receivedSykmelding: ReceivedSykmelding,
        val validationResult: ValidationResult,
        val apprec: Apprec
)