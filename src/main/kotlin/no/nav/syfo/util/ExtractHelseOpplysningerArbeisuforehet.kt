package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLDocument
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.TeleCom
import no.nav.syfo.logger

fun extractHelseOpplysningerArbeidsuforhet(
    fellesformat: XMLEIFellesformat
): Pair<HelseOpplysningerArbeidsuforhet, Boolean> {

    val (helseOpplysningerArbeidsuforhet, recursive) =
        tryGetHelseOpplysningerArbeidsuforhet(fellesformat.get<XMLMsgHead>())

    requireNotNull(helseOpplysningerArbeidsuforhet) {
        RuntimeException("Chould not find HelseOpplysningerArbeidsuforhet in message")
    }
    return helseOpplysningerArbeidsuforhet to recursive
}

fun tryGetHelseOpplysningerArbeidsuforhet(
    XMLMsgHead: XMLMsgHead
): Pair<HelseOpplysningerArbeidsuforhet?, Boolean> {
    XMLMsgHead.document.forEach {
        val helseOpplysningerArbeidsuforhet = tryGetHelseOpplysningerArbeidsuforhet(it)
        if (helseOpplysningerArbeidsuforhet.first != null) {
            return helseOpplysningerArbeidsuforhet
        }
    }
    return null to false
}

fun tryGetHelseOpplysningerArbeidsuforhet(
    document: XMLDocument
): Pair<HelseOpplysningerArbeidsuforhet?, Boolean> {
    document.refDoc.content.any.forEach {
        if (it is HelseOpplysningerArbeidsuforhet) {
            return it as HelseOpplysningerArbeidsuforhet to false
        } else if (it is XMLMsgHead) {
            logger.warn("Bad xml format, trying recursion")
            return tryGetHelseOpplysningerArbeidsuforhet(it).first to true
        }
    }
    return null to false
}

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find { it.typeId.v == "ENH" }

fun extractOrganisationRashNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find { it.typeId.v == "RSH" }

fun extractOrganisationHerNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find { it.typeId.v == "HER" }

fun extractHprOrganization(fellesformat: XMLEIFellesformat): String? =
    fellesformat
        .get<XMLMsgHead>()
        .msgInfo
        .sender
        .organisation
        .healthcareProfessional
        ?.ident
        ?.find { it.typeId.v == "HPR" }
        ?.id
        ?.let { padHpr(it.trim()) }

fun extractFnrDnrFraBehandler(healthInformation: HelseOpplysningerArbeidsuforhet): String? =
    healthInformation.behandler.id.find { it.typeId.v == "FNR" || it.typeId.v == "DNR" }?.id

fun extractHprBehandler(healthInformation: HelseOpplysningerArbeidsuforhet): String? =
    healthInformation.behandler.id.find { it.typeId.v == "HPR" }?.id?.let { padHpr(it.trim()) }

fun extractTlfFromKontaktInfo(kontaktInfo: List<TeleCom>): String? {

    val phoneNumber =
        kontaktInfo
            .find {
                it.teleAddress?.v?.contains("tel:") == true &&
                    (it.typeTelecom
                        ?.v
                        ?.contains(
                            "HP",
                        ) == true || it?.typeTelecom?.dn?.contains("Hovedtelefon") == true)
            }
            ?.teleAddress
            ?.v
            ?.removePrefix("tel:")

    val email =
        kontaktInfo
            .find { it.teleAddress?.v?.contains("mailto:") == true }
            ?.teleAddress
            ?.v
            ?.removePrefix("mailto:")

    return phoneNumber ?: (email ?: kontaktInfo.firstOrNull()?.teleAddress?.v)
}

fun padHpr(hprnummer: String?): String? {
    return if (hprnummer?.length == null || hprnummer.length == 9) {
        hprnummer
    } else if (hprnummer.length < 9) {
        hprnummer.padStart(9, '0')
    } else {
        null
    }
}

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T
