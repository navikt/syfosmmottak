package no.nav.syfo

import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.utils.getFileAsString
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.fellesformatUnmarshaller
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader

object ApprecMapperSpek : Spek({
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
    val msgHead = fellesformat.get<XMLMsgHead>()

    describe("Duplicate AppRec") {
        val tekstTilSykmelder = "Duplikat! - Denne sykmeldingen er mottatt tidligere. \" +\n" +
        "                                        \"Skal ikke sendes på nytt"
        val apprec = fellesformat.toApprec(
                ediloggid = receiverBlock.ediLoggId,
                msgId = msgHead.msgInfo.msgId,
                xmlMsgHead = msgHead,
                apprecStatus = ApprecStatus.AVVIST,
                tekstTilSykmelder = tekstTilSykmelder,
                senderOrganisation = msgHead.msgInfo.receiver.organisation,
                mottakerOrganisation = msgHead.msgInfo.sender.organisation
        )

        it("Has the same ediLoggId as the source") {
            apprec.ediloggid shouldEqual fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
        }
        it("Has the same msgId as the source") {
            apprec.msgId shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.msgId
        }
        it("Has the same genDate as the source") {
            apprec.genDate shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.genDate
        }
        it("Has the same msgTypeVerdi as the source") {
            apprec.msgTypeVerdi shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.type.v
        }
        it("Has the same msgTypeBeskrivelse as the source") {
            apprec.msgTypeBeskrivelse shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.type.dn
        }
        it("Has the same apprecStatusDN as the source") {
            apprec.apprecStatus.dn shouldEqual ApprecStatus.AVVIST.dn
        }
        it("Has the same apprecStatusv as the source") {
            apprec.apprecStatus.v shouldEqual ApprecStatus.AVVIST.v
        }
        it("Has the same tekstTilSykmelder as the source") {
            apprec.tekstTilSykmelder shouldEqual tekstTilSykmelder
        }
        it("Has the same id on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id
        }
        it("Has the same id.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn
        }
        it("Has the same id.typeid.v on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v
        }
        it("Has the same organisationName on the sender organisation") {
            apprec.mottakerOrganisasjon.navn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName
        }
        it("Has the same id on additionalIds on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id
        }
        it("Has the same id on additionalIds.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn
        }
        it("Has the same id on additionalIds.typeId.v on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v
        }
        it("Has the same healthcareProfessional name on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.navn shouldEqual "Sødal Ingvild Fos"
        }
        it("Has the same healthcareProfessional ident on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional.ident?.first()?.id
        }
        it("Has the same id.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn
        }
        it("Has the same id.typeid.v on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v
        }
        it("Has the same organisationName on the receiver organisation") {
            apprec.senderOrganisasjon.navn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName
        }
        it("Has the same id on additionalIds on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id
        }
        it("Has the same id on additionalIds.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn
        }
        it("Has the same id on additionalIds.typeId.v on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v
        }
        it("Has the same healthcareProfessional name on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.navn shouldEqual null
        }
        it("Has the same healthcareProfessional ident on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.hovedIdent?.id shouldEqual null
        }
    }

    describe("OK AppRec") {
        val apprec = fellesformat.toApprec(
                ediloggid = receiverBlock.ediLoggId,
                msgId = msgHead.msgInfo.msgId,
                xmlMsgHead = msgHead,
                apprecStatus = ApprecStatus.OK,
                tekstTilSykmelder = null,
                mottakerOrganisation = msgHead.msgInfo.sender.organisation,
                senderOrganisation = msgHead.msgInfo.receiver.organisation
        )
        it("Has the same ediLoggId as the source") {
            apprec.ediloggid shouldEqual fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
        }
        it("Has the same msgId as the source") {
            apprec.msgId shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.msgId
        }
        it("Has the same genDate as the source") {
            apprec.genDate shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.genDate
        }
        it("Has the same msgTypeVerdi as the source") {
            apprec.msgTypeVerdi shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.type.v
        }
        it("Has the same msgTypeBeskrivelse as the source") {
            apprec.msgTypeBeskrivelse shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.type.dn
        }
        it("Has the same apprecStatusDN as the source") {
            apprec.apprecStatus.dn shouldEqual ApprecStatus.OK.dn
        }
        it("Has the same apprecStatusv as the source") {
            apprec.apprecStatus.v shouldEqual ApprecStatus.OK.v
        }
        it("Has the same tekstTilSykmelder as the source") {
            apprec.tekstTilSykmelder shouldEqual null
        }
        it("Has the same id on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id
        }
        it("Has the same id.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn
        }
        it("Has the same id.typeid.v on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v
        }
        it("Has the same organisationName on the sender organisation") {
            apprec.mottakerOrganisasjon.navn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName
        }
        it("Has the same id on additionalIds on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id
        }
        it("Has the same id on additionalIds.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn
        }
        it("Has the same id on additionalIds.typeId.v on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v
        }
        it("Has the same healthcareProfessional name on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.navn shouldEqual "Sødal Ingvild Fos"
        }
        it("Has the same healthcareProfessional ident on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional.ident?.first()?.id
        }
        it("Has the same id.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn
        }
        it("Has the same id.typeid.v on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v
        }
        it("Has the same organisationName on the receiver organisation") {
            apprec.senderOrganisasjon.navn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName
        }
        it("Has the same id on additionalIds on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id
        }
        it("Has the same id on additionalIds.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn
        }
        it("Has the same id on additionalIds.typeId.v on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v
        }
        it("Has the same healthcareProfessional name on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.navn shouldEqual null
        }
        it("Has the same healthcareProfessional ident on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.hovedIdent?.id shouldEqual null
        }
    }

    describe("Avisst AppRec with validationResult") {

        val validationResult = ValidationResult(status = Status.INVALID, ruleHits = listOf(
            RuleInfo(ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                    messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                    messageForSender = "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med " +
                            "autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)",
                    ruleStatus = Status.INVALID
            ),
            RuleInfo(ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                    messageForUser = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                    messageForSender = "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                    ruleStatus = Status.INVALID
            )))

        val apprec = fellesformat.toApprec(
                ediloggid = receiverBlock.ediLoggId,
                msgId = msgHead.msgInfo.msgId,
                xmlMsgHead = msgHead,
                apprecStatus = ApprecStatus.OK,
                tekstTilSykmelder = null,
                mottakerOrganisation = msgHead.msgInfo.sender.organisation,
                senderOrganisation = msgHead.msgInfo.receiver.organisation,
                validationResult = validationResult

        )
        it("Has the same validationResult as the source") {
            apprec.validationResult shouldEqual validationResult
        }

        it("Has the same ediLoggId as the source") {
            apprec.ediloggid shouldEqual fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
        }
        it("Has the same msgId as the source") {
            apprec.msgId shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.msgId
        }
        it("Has the same genDate as the source") {
            apprec.genDate shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.genDate
        }
        it("Has the same msgTypeVerdi as the source") {
            apprec.msgTypeVerdi shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.type.v
        }
        it("Has the same msgTypeBeskrivelse as the source") {
            apprec.msgTypeBeskrivelse shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.type.dn
        }
        it("Has the same apprecStatusDN as the source") {
            apprec.apprecStatus.dn shouldEqual ApprecStatus.OK.dn
        }
        it("Has the same apprecStatusv as the source") {
            apprec.apprecStatus.v shouldEqual ApprecStatus.OK.v
        }
        it("Has the same tekstTilSykmelder as the source") {
            apprec.tekstTilSykmelder shouldEqual null
        }
        it("Has the same id on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id
        }
        it("Has the same id.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn
        }
        it("Has the same id.typeid.v on the sender organisation") {
            apprec.mottakerOrganisasjon.hovedIdent.typeId.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v
        }
        it("Has the same organisationName on the sender organisation") {
            apprec.mottakerOrganisasjon.navn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName
        }
        it("Has the same id on additionalIds on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id
        }
        it("Has the same id on additionalIds.typeid.dn on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn
        }
        it("Has the same id on additionalIds.typeId.v on the sender organisation") {
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v
        }
        it("Has the same healthcareProfessional name on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.navn shouldEqual "Sødal Ingvild Fos"
        }
        it("Has the same healthcareProfessional ident on the sender organisation") {
            apprec.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.healthcareProfessional.ident?.first()?.id
        }
        it("Has the same id.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn
        }
        it("Has the same id.typeid.v on the receiver organisation") {
            apprec.senderOrganisasjon.hovedIdent.typeId.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v
        }
        it("Has the same organisationName on the receiver organisation") {
            apprec.senderOrganisasjon.navn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName
        }
        it("Has the same id on additionalIds on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id
        }
        it("Has the same id on additionalIds.typeid.dn on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn
        }
        it("Has the same id on additionalIds.typeId.v on the receiver organisation") {
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v
        }
        it("Has the same healthcareProfessional name on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.navn shouldEqual null
        }
        it("Has the same healthcareProfessional ident on the receiver organisation") {
            apprec.senderOrganisasjon.helsepersonell?.hovedIdent?.id shouldEqual null
        }
    }
})