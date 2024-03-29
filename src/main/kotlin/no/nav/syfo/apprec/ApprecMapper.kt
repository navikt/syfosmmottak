package no.nav.syfo.apprec

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLCV as MsgHeadCV
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLOrganisation
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.getLocalDateTime

fun XMLEIFellesformat.toApprec(
    ediloggid: String,
    msgId: String,
    xmlMsgHead: XMLMsgHead,
    apprecStatus: ApprecStatus,
    tekstTilSykmelder: String? = null,
    senderOrganisation: XMLOrganisation,
    mottakerOrganisation: XMLOrganisation,
    msgGenDate: String,
    validationResult: ValidationResult? = null,
    ebService: String? = null,
) =
    Apprec(
        ediloggid = ediloggid,
        msgId = msgId,
        msgTypeVerdi = xmlMsgHead.msgInfo.type.v,
        msgTypeBeskrivelse = xmlMsgHead.msgInfo.type?.dn ?: "",
        genDate = getLocalDateTime(xmlMsgHead.msgInfo.genDate),
        apprecStatus = apprecStatus,
        tekstTilSykmelder = tekstTilSykmelder,
        senderOrganisasjon = senderOrganisation.intoHCP(),
        mottakerOrganisasjon = mottakerOrganisation.intoHCP(),
        msgGenDate = msgGenDate,
        validationResult = validationResult,
        ebService = ebService,
    )

fun XMLHealthcareProfessional.intoHCPerson(): Helsepersonell =
    Helsepersonell(
        navn =
            if (middleName == null) "$familyName $givenName"
            else "$familyName $givenName $middleName",
        hovedIdent = ident.first().intoInst(),
        typeId = ident.first().typeId.intoKodeverdier(),
        tilleggsIdenter = getTilleggsIdenter(ident),
    )

fun XMLOrganisation.intoHCP(): Organisation =
    Organisation(
        hovedIdent = ident.first().intoInst(),
        navn = organisationName,
        tilleggsIdenter = getTilleggsIdenter(ident),
        helsepersonell =
            when (healthcareProfessional != null) {
                true -> healthcareProfessional.intoHCPerson()
                else -> null
            },
    )

fun getTilleggsIdenter(ident: List<XMLIdent>): List<Ident> {
    return ident.drop(1).mapNotNull {
        if (it.typeId.v == null) {
            null
        } else {
            Ident(it.id, it.typeId.intoKodeverdier())
        }
    }
}

fun XMLIdent.intoInst(): Ident {
    val ident = this
    return Ident(ident.id, ident.typeId.intoKodeverdier())
}

fun MsgHeadCV.intoKodeverdier(): Kodeverdier {
    val msgHeadCV = this
    return Kodeverdier(msgHeadCV.dn ?: "", msgHeadCV.v)
}

operator fun MutableList<Ident>.plusAssign(idents: Iterable<Ident>) {
    this.addAll(idents.map { it.intotillegsIdenterId() })
}

fun Ident.intotillegsIdenterId(): Ident {
    val ident = this
    return Ident(ident.id, ident.typeId)
}
