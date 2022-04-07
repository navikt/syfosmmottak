package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getLocalDateTime
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeEqualTo
import java.io.StringReader

class ApprecMapperSpek : FunSpec({
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
    val msgHead = fellesformat.get<XMLMsgHead>()

    context("Duplicate AppRec") {
        val tekstTilSykmelder = "Duplikat! - Denne sykmeldingen er mottatt tidligere. \" +\n" +
            "                                        \"Skal ikke sendes på nytt"
        val apprec = fellesformat.toApprec(
            ediloggid = receiverBlock.ediLoggId,
            msgId = msgHead.msgInfo.msgId,
            xmlMsgHead = msgHead,
            apprecStatus = ApprecStatus.AVVIST,
            tekstTilSykmelder = tekstTilSykmelder,
            senderOrganisation = msgHead.msgInfo.receiver.organisation,
            mottakerOrganisation = msgHead.msgInfo.sender.organisation,
            msgGenDate = msgHead.msgInfo.genDate
        )

        test("Has same msgGenDate") {
            apprec.msgGenDate shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.genDate
        }

        test("Has the same ediLoggId as the source") {
            apprec.ediloggid shouldBeEqualTo fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
        }
        test("Has the same msgId as the source") {
            apprec.msgId shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.msgId
        }
        test("Has the same genDate as the source") {
            apprec.genDate shouldBeEqualTo getLocalDateTime(fellesformat.get<XMLMsgHead>().msgInfo.genDate)
        }
        test("Has the same msgTypeVerdi as the source") {
            apprec.msgTypeVerdi shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.type.v
        }
        test("Has the same msgTypeBeskrivelse as the source") {
            apprec.msgTypeBeskrivelse shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.type.dn
        }
        test("Has the same apprecStatusDN as the source") {
            apprec.apprecStatus.dn shouldBeEqualTo ApprecStatus.AVVIST.dn
        }
        test("Has the same apprecStatusv as the source") {
            apprec.apprecStatus.v shouldBeEqualTo ApprecStatus.AVVIST.v
        }
        test("Has the same tekstTilSykmelder as the source") {
            apprec.tekstTilSykmelder shouldBeEqualTo tekstTilSykmelder
        }
        test("Has the same id on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id
        }
        test("Has the same id.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn
        }
        test("Has the same id.typeid.v on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v
        }
        test("Has the same organisationName on the sender organisation") {
            apprec.mottakerOrganisasjon.navn shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName
        }
        test("Has the same id on additionalIds on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id
        }
        test("Has the same id on additionalIds.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn
        }
        test("Has the same id on additionalIds.typeId.v on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v
        }
        test("Has the same healthcareProfessional name on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.navn shouldBeEqualTo "Sødal Ingvild Fos"
        }
        test("Has the same healthcareProfessional ident on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional.ident?.first()?.id
        }
        test("Has the same id.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn
        }
        test("Has the same id.typeid.v on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v
        }
        test("Has the same organisationName on the receiver organisation") {
            apprec.senderOrganisasjon.navn shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName
        }
        test("Has the same id on additionalIds on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id
        }
        test("Has the same id on additionalIds.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn
        }
        test("Has the same id on additionalIds.typeId.v on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v
        }
        test("Has the same healthcareProfessional name on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.navn shouldBeEqualTo null
        }
        test("Has the same healthcareProfessional ident on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.hovedIdent?.id shouldBeEqualTo null
        }
    }

    context("OK AppRec") {
        val apprec = fellesformat.toApprec(
            ediloggid = receiverBlock.ediLoggId,
            msgId = msgHead.msgInfo.msgId,
            xmlMsgHead = msgHead,
            apprecStatus = ApprecStatus.OK,
            tekstTilSykmelder = null,
            mottakerOrganisation = msgHead.msgInfo.sender.organisation,
            senderOrganisation = msgHead.msgInfo.receiver.organisation,
            msgGenDate = msgHead.msgInfo.genDate
        )
        test("Has the same ediLoggId as the source") {
            apprec.ediloggid shouldBeEqualTo fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
        }
        test("Has the same msgId as the source") {
            apprec.msgId shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.msgId
        }
        test("Has the same genDate as the source") {
            apprec.genDate shouldBeEqualTo getLocalDateTime(fellesformat.get<XMLMsgHead>().msgInfo.genDate)
        }
        test("Has the same msgTypeVerdi as the source") {
            apprec.msgTypeVerdi shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.type.v
        }
        test("Has the same msgTypeBeskrivelse as the source") {
            apprec.msgTypeBeskrivelse shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.type.dn
        }
        test("Has the same apprecStatusDN as the source") {
            apprec.apprecStatus.dn shouldBeEqualTo ApprecStatus.OK.dn
        }
        test("Has the same apprecStatusv as the source") {
            apprec.apprecStatus.v shouldBeEqualTo ApprecStatus.OK.v
        }
        test("Has the same tekstTilSykmelder as the source") {
            apprec.tekstTilSykmelder shouldBeEqualTo null
        }
        test("Has the same id on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id
        }
        test("Has the same id.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn
        }
        test("Has the same id.typeid.v on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v
        }
        test("Has the same organisationName on the sender organisation") {
            apprec.mottakerOrganisasjon.navn shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName
        }
        test("Has the same id on additionalIds on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id
        }
        test("Has the same id on additionalIds.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn
        }
        test("Has the same id on additionalIds.typeId.v on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v
        }
        test("Has the same healthcareProfessional name on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.navn shouldBeEqualTo "Sødal Ingvild Fos"
        }
        test("Has the same healthcareProfessional ident on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional.ident?.first()?.id
        }
        test("Has the same id.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn
        }
        test("Has the same id.typeid.v on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v
        }
        test("Has the same organisationName on the receiver organisation") {
            apprec.senderOrganisasjon.navn shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName
        }
        test("Has the same id on additionalIds on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id
        }
        test("Has the same id on additionalIds.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn
        }
        test("Has the same id on additionalIds.typeId.v on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v
        }
        test("Has the same healthcareProfessional name on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.navn shouldBeEqualTo null
        }
        test("Has the same healthcareProfessional ident on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.hovedIdent?.id shouldBeEqualTo null
        }
    }

    context("Avisst AppRec with validationResult") {

        val validationResult = ValidationResult(
            status = Status.INVALID,
            ruleHits = listOf(
                RuleInfo(
                    ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                    messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                    messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med " +
                        "autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)",
                    ruleStatus = Status.INVALID
                ),
                RuleInfo(
                    ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                    messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                    messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                    ruleStatus = Status.INVALID
                )
            )
        )

        val apprec = fellesformat.toApprec(
            ediloggid = receiverBlock.ediLoggId,
            msgId = msgHead.msgInfo.msgId,
            xmlMsgHead = msgHead,
            apprecStatus = ApprecStatus.OK,
            tekstTilSykmelder = null,
            mottakerOrganisation = msgHead.msgInfo.sender.organisation,
            senderOrganisation = msgHead.msgInfo.receiver.organisation,
            validationResult = validationResult,
            msgGenDate = msgHead.msgInfo.genDate
        )
        test("Has the same validationResult as the source") {
            apprec.validationResult shouldBeEqualTo validationResult
        }

        test("Has the same ediLoggId as the source") {
            apprec.ediloggid shouldBeEqualTo fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
        }
        test("Has the same msgId as the source") {
            apprec.msgId shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.msgId
        }
        test("Has the same genDate as the source") {
            apprec.genDate shouldBeEqualTo getLocalDateTime(fellesformat.get<XMLMsgHead>().msgInfo.genDate)
        }
        test("Has the same msgTypeVerdi as the source") {
            apprec.msgTypeVerdi shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.type.v
        }
        test("Has the same msgTypeBeskrivelse as the source") {
            apprec.msgTypeBeskrivelse shouldBeEqualTo fellesformat.get<XMLMsgHead>().msgInfo.type.dn
        }
        test("Has the same apprecStatusDN as the source") {
            apprec.apprecStatus.dn shouldBeEqualTo ApprecStatus.OK.dn
        }
        test("Has the same apprecStatusv as the source") {
            apprec.apprecStatus.v shouldBeEqualTo ApprecStatus.OK.v
        }
        test("Has the same tekstTilSykmelder as the source") {
            apprec.tekstTilSykmelder shouldBeEqualTo null
        }
        test("Has the same id on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id
        }
        test("Has the same id.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn
        }
        test("Has the same id.typeid.v on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v
        }
        test("Has the same organisationName on the sender organisation") {
            apprec.mottakerOrganisasjon.navn shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName
        }
        test("Has the same id on additionalIds on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id
        }
        test("Has the same id on additionalIds.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn
        }
        test("Has the same id on additionalIds.typeId.v on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v
        }
        test("Has the same healthcareProfessional name on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.navn shouldBeEqualTo "Sødal Ingvild Fos"
        }
        test("Has the same healthcareProfessional ident on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional.ident?.first()?.id
        }
        test("Has the same id.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn
        }
        test("Has the same id.typeid.v on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v
        }
        test("Has the same organisationName on the receiver organisation") {
            apprec.senderOrganisasjon.navn shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName
        }
        test("Has the same id on additionalIds on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.id shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id
        }
        test("Has the same id on additionalIds.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn
        }
        test("Has the same id on additionalIds.typeId.v on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldBeEqualTo
                fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v
        }
        test("Has the same healthcareProfessional name on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.navn shouldBeEqualTo null
        }
        test("Has the same healthcareProfessional ident on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.hovedIdent?.id shouldBeEqualTo null
        }
    }
})
