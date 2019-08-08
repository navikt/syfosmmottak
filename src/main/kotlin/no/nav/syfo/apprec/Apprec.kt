package no.nav.syfo.apprec

import no.nav.syfo.model.ValidationResult

data class Apprec(
    val fellesformat: String,
    val apprecStatus: ApprecStatus,
    val tekstTilSykmelder: String? = null,
    val validationResult: ValidationResult? = null
)
