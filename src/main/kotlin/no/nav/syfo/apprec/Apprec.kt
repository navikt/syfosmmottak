package no.nav.syfo.apprec

import java.time.LocalDateTime
import no.nav.syfo.model.ValidationResult

data class Apprec(
    val ediloggid: String,
    val msgId: String,
    val msgTypeV: String,
    val msgTypeDN: String,
    val genDate: LocalDateTime,
    val apprecStatus: ApprecStatus,
    val tekstTilSykmelder: String? = null,
    val senderOrganisasjon: Organisation,
    val mottakerOrganisasjon: Organisation,
    val validationResult: ValidationResult?
)

data class Organisation(
    val houvedIdent: Ident,
    val navn: String,
    val tillegsIdenter: List<Ident>? = listOf(),
    val helsepersonell: Helsepersonell? = null
)

data class Helsepersonell(
    val navn: String,
    val houvedIdent: Ident,
    val typeId: CS,
    val tillegsIdenter: List<Ident>?

)

data class Ident(
    val id: String,
    val typeId: CS
)

data class CS(
    val dn: String,
    val v: String
)
