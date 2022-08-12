package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.TeleCom

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
    fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "ENH"
    }

fun extractOrganisationRashNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "RSH"
    }

fun extractOrganisationHerNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
        it.typeId.v == "HER"
    }

fun extractHpr(fellesformat: XMLEIFellesformat): XMLIdent? =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional?.ident?.find {
        it.typeId.v == "HPR"
    }

fun extractFnrDnrFraBehandler(healthInformation: HelseOpplysningerArbeidsuforhet): String? =
    healthInformation.behandler.id.find { it.typeId.v == "FNR" || it.typeId.v == "DNR" }?.id

fun extractTlfFromKontaktInfo(kontaktInfo: List<TeleCom>?): String? =
    if (kontaktInfo?.size != 0 && kontaktInfo?.firstOrNull()!!.teleAddress != null &&
        kontaktInfo.firstOrNull()!!.teleAddress?.v?.contains("tel:") == true
    ) {
        kontaktInfo.firstOrNull()!!.teleAddress?.v?.removePrefix("tel:")
    } else ""

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T
