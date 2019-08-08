package no.nav.syfo.apprec

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.syfo.model.ValidationResult

data class Apprec(
    val fellesformat: XMLEIFellesformat,
    val apprecStatus: ApprecStatus,
    val tekstTilSykmelder: String? = null,
    val validationResult: ValidationResult? = null
)
