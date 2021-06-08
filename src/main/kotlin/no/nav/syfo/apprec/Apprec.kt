package no.nav.syfo.apprec

import no.nav.syfo.model.ValidationResult
import java.time.LocalDateTime

data class Apprec(
    val ediloggid: String,
    val msgId: String,
    val msgTypeVerdi: String,
    val msgTypeBeskrivelse: String,
    val genDate: LocalDateTime,
    val apprecStatus: ApprecStatus,
    val tekstTilSykmelder: String? = null,
    val senderOrganisasjon: Organisation,
    val mottakerOrganisasjon: Organisation,
    val msgGenDate: String,
    val validationResult: ValidationResult?
)

data class Organisation(
    val hovedIdent: Ident,
    val navn: String,
    val tilleggsIdenter: List<Ident>? = listOf(),
    val helsepersonell: Helsepersonell? = null
)

data class Helsepersonell(
    val navn: String,
    val hovedIdent: Ident,
    val typeId: Kodeverdier,
    val tilleggsIdenter: List<Ident>?

)

data class Ident(
    val id: String,
    val typeId: Kodeverdier
)

data class Kodeverdier(
    val beskrivelse: String,
    val verdi: String
)
