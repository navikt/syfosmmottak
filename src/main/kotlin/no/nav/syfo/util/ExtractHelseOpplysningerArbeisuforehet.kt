package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.LocalTime
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
        fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet

fun extractSyketilfelleStartDato(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): LocalDateTime =
        LocalDateTime.of(helseOpplysningerArbeidsuforhet.syketilfelleStartDato, LocalTime.NOON)

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
        fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional.ident.find {
            it.typeId.v == "HPR"
        }

fun hprManglerFraSignatur(fellesformat: XMLEIFellesformat): Boolean =
    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional.ident.find {
        it.typeId.v == "HPR"
    }?.id.isNullOrBlank()

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T
