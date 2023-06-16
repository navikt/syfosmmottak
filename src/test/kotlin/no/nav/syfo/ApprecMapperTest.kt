package no.nav.syfo

import java.io.StringReader
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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ApprecMapperTest {
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat =
        fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
    val msgHead = fellesformat.get<XMLMsgHead>()

    val tekstTilSykmelder =
        "Duplikat! - Denne sykmeldingen er mottatt tidligere. \" +\n" +
            "                                        \"Skal ikke sendes på nytt"
    val apprecAvvist =
        fellesformat.toApprec(
            ediloggid = receiverBlock.ediLoggId,
            msgId = msgHead.msgInfo.msgId,
            xmlMsgHead = msgHead,
            apprecStatus = ApprecStatus.AVVIST,
            tekstTilSykmelder = tekstTilSykmelder,
            senderOrganisation = msgHead.msgInfo.receiver.organisation,
            mottakerOrganisation = msgHead.msgInfo.sender.organisation,
            msgGenDate = msgHead.msgInfo.genDate,
        )

    val apprecOK =
        fellesformat.toApprec(
            ediloggid = receiverBlock.ediLoggId,
            msgId = msgHead.msgInfo.msgId,
            xmlMsgHead = msgHead,
            apprecStatus = ApprecStatus.OK,
            tekstTilSykmelder = null,
            mottakerOrganisation = msgHead.msgInfo.sender.organisation,
            senderOrganisation = msgHead.msgInfo.receiver.organisation,
            msgGenDate = msgHead.msgInfo.genDate,
        )

    val validationResult =
        ValidationResult(
            status = Status.INVALID,
            ruleHits =
                listOf(
                    RuleInfo(
                        ruleName = "BEHANDLER_KI_NOT_USING_VALID_DIAGNOSECODE_TYPE",
                        messageForUser = "Den som skrev sykmeldingen mangler autorisasjon.",
                        messageForSender =
                            "Behandler er manuellterapeut/kiropraktor eller fysioterapeut med " +
                                "autorisasjon har angitt annen diagnose enn kapitel L (muskel og skjelettsykdommer)",
                        ruleStatus = Status.INVALID,
                    ),
                    RuleInfo(
                        ruleName = "NUMBER_OF_TREATMENT_DAYS_SET",
                        messageForUser =
                            "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                        messageForSender =
                            "Hvis behandlingsdager er angitt sendes meldingen til manuell behandling.",
                        ruleStatus = Status.INVALID,
                    ),
                ),
        )

    val apprec =
        fellesformat.toApprec(
            ediloggid = receiverBlock.ediLoggId,
            msgId = msgHead.msgInfo.msgId,
            xmlMsgHead = msgHead,
            apprecStatus = ApprecStatus.OK,
            tekstTilSykmelder = null,
            mottakerOrganisation = msgHead.msgInfo.sender.organisation,
            senderOrganisation = msgHead.msgInfo.receiver.organisation,
            validationResult = validationResult,
            msgGenDate = msgHead.msgInfo.genDate,
        )

    @Test
    internal fun `Duplicate AppRec has same msgGenDate`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.genDate,
            apprecAvvist.msgGenDate
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same ediLoggId as the source`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMottakenhetBlokk>().ediLoggId,
            apprecAvvist.ediloggid
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same msgId as the source`() {
        Assertions.assertEquals(fellesformat.get<XMLMsgHead>().msgInfo.msgId, apprecAvvist.msgId)
    }

    @Test
    internal fun `Duplicate AppRec has the same genDate as the source`() {
        Assertions.assertEquals(
            getLocalDateTime(fellesformat.get<XMLMsgHead>().msgInfo.genDate),
            apprecAvvist.genDate
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same msgTypeVerdi as the source`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.type.v,
            apprecAvvist.msgTypeVerdi
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same msgTypeBeskrivelse as the source`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.type.dn,
            apprecAvvist.msgTypeBeskrivelse
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same apprecStatusDN as the source`() {
        Assertions.assertEquals(ApprecStatus.AVVIST.dn, apprecAvvist.apprecStatus.dn)
    }

    @Test
    internal fun `Duplicate AppRec has the same apprecStatusv as the source`() {
        Assertions.assertEquals(ApprecStatus.AVVIST.v, apprecAvvist.apprecStatus.v)
    }

    @Test
    internal fun `Duplicate AppRec has the same tekstTilSykmelder as the source`() {
        Assertions.assertEquals(tekstTilSykmelder, apprecAvvist.tekstTilSykmelder)
    }

    @Test
    internal fun `Duplicate AppRec has the same id on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id,
            apprecAvvist.mottakerOrganisasjon.hovedIdent.id,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id typeid dn on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn,
            apprecAvvist.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id typeid v on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v,
            apprecAvvist.mottakerOrganisasjon.hovedIdent.typeId.verdi,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same organisationName on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName,
            apprecAvvist.mottakerOrganisasjon.navn,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id on additionalIds on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id,
            apprecAvvist.mottakerOrganisasjon.tilleggsIdenter?.last()?.id,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id on additionalIds typeid dn on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn,
            apprecAvvist.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id on additionalIds typeId v on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v,
            apprecAvvist.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same healthcareProfessional name on the sender organisation`() {
        Assertions.assertEquals(
            "Frost Frida Perma",
            apprecAvvist.mottakerOrganisasjon.helsepersonell?.navn
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same healthcareProfessional ident on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat
                .get<XMLMsgHead>()
                .msgInfo
                .sender
                .organisation
                .healthcareProfessional
                .ident
                ?.first()
                ?.id,
            apprecAvvist.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id typeid dn on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn,
            apprecAvvist.senderOrganisasjon.hovedIdent.typeId.beskrivelse,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id typeid v on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v,
            apprecAvvist.senderOrganisasjon.hovedIdent.typeId.verdi,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same organisationName on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName,
            apprecAvvist.senderOrganisasjon.navn,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id on additionalIds on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id,
            apprecAvvist.senderOrganisasjon.tilleggsIdenter?.last()?.id,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id on additionalIds typeid dn on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn,
            apprecAvvist.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same id on additionalIds typeId v on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v,
            apprecAvvist.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi,
        )
    }

    @Test
    internal fun `Duplicate AppRec has the same healthcareProfessional name on the receiver organisation`() {
        Assertions.assertEquals(null, apprecAvvist.senderOrganisasjon.helsepersonell?.navn)
    }

    @Test
    internal fun `Duplicate AppRec has the same healthcareProfessional ident on the receiver organisation`() {
        Assertions.assertEquals(
            null,
            apprecAvvist.senderOrganisasjon.helsepersonell?.hovedIdent?.id
        )
    }

    @Test
    internal fun `OK AppRec has the same ediLoggId as the source`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMottakenhetBlokk>().ediLoggId,
            apprecOK.ediloggid,
        )
    }

    @Test
    internal fun `OK AppRec has the same msgId as the source`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.msgId,
            apprecOK.msgId,
        )
    }

    @Test
    internal fun `OK AppRec has the same genDate as the source`() {
        Assertions.assertEquals(
            getLocalDateTime(fellesformat.get<XMLMsgHead>().msgInfo.genDate),
            apprecOK.genDate,
        )
    }

    @Test
    internal fun `OK AppRec has the same msgTypeVerdi as the source`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.type.v,
            apprecOK.msgTypeVerdi
        )
    }

    @Test
    internal fun `OK AppRec has the same msgTypeBeskrivelse as the source`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.type.dn,
            apprecOK.msgTypeBeskrivelse,
        )
    }

    @Test
    internal fun `OK AppRec has the same apprecStatusDN as the source`() {
        Assertions.assertEquals(ApprecStatus.OK.dn, apprecOK.apprecStatus.dn)
    }

    @Test
    internal fun `OK AppRec has the same apprecStatusv as the source`() {
        Assertions.assertEquals(ApprecStatus.OK.v, apprecOK.apprecStatus.v)
    }

    @Test
    internal fun `OK AppRec has the same tekstTilSykmelder as the source`() {
        Assertions.assertEquals(null, apprecOK.tekstTilSykmelder)
    }

    @Test
    internal fun `OK AppRec has the same id on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id,
            apprecOK.mottakerOrganisasjon.hovedIdent.id,
        )
    }

    @Test
    internal fun `OK AppRec has the same id typeid dn on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn,
            apprecOK.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse,
        )
    }

    @Test
    internal fun `OK AppRec has the same id typeid v on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v,
            apprecOK.mottakerOrganisasjon.hovedIdent.typeId.verdi,
        )
    }

    @Test
    internal fun `OK AppRec has the same organisationName on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName,
            apprecOK.mottakerOrganisasjon.navn,
        )
    }

    @Test
    internal fun `OK AppRec has the same id on additionalIds on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id,
            apprecOK.mottakerOrganisasjon.tilleggsIdenter?.last()?.id,
        )
    }

    @Test
    internal fun `OK AppRec has the same id on additionalIds typeid dn on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn,
            apprecOK.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse,
        )
    }

    @Test
    internal fun `OK AppRec has the same id on additionalIds typeId v on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v,
            apprecOK.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi,
        )
    }

    @Test
    internal fun `OK AppRec has the same healthcareProfessional name on the sender organisation`() {
        Assertions.assertEquals(
            "Frost Frida Perma",
            apprecOK.mottakerOrganisasjon.helsepersonell?.navn
        )
    }

    @Test
    internal fun `OK AppRec has the same healthcareProfessional ident on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat
                .get<XMLMsgHead>()
                .msgInfo
                .sender
                .organisation
                .healthcareProfessional
                .ident
                ?.first()
                ?.id,
            apprecOK.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id,
        )
    }

    @Test
    internal fun `OK AppRec has the same id typeid dn on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn,
            apprecOK.senderOrganisasjon.hovedIdent.typeId.beskrivelse,
        )
    }

    @Test
    internal fun `OK AppRec has the same id typeid v on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v,
            apprecOK.senderOrganisasjon.hovedIdent.typeId.verdi,
        )
    }

    @Test
    internal fun `OK AppRec has the same organisationName on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName,
            apprec.senderOrganisasjon.navn,
        )
    }

    @Test
    internal fun `OK AppRec has the same id on additionalIds on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id,
            apprecOK.senderOrganisasjon.tilleggsIdenter?.last()?.id,
        )
    }

    @Test
    internal fun `OK AppRec has the same id on additionalIds typeid dn on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn,
            apprecOK.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse,
        )
    }

    @Test
    internal fun `OK AppRec has the same id on additionalIds typeId v on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v,
            apprecOK.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi,
        )
    }

    @Test
    internal fun `OK AppRec has the same healthcareProfessional name on the receiver organisation`() {
        Assertions.assertEquals(null, apprecOK.senderOrganisasjon.helsepersonell?.navn)
    }

    @Test
    internal fun `OK AppRec has the same healthcareProfessional ident on the receiver organisation`() {
        Assertions.assertEquals(null, apprecOK.senderOrganisasjon.helsepersonell?.hovedIdent?.id)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same validationResult as the source`() {
        Assertions.assertEquals(validationResult, apprec.validationResult)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same ediLoggId as the source`() {
        Assertions.assertEquals(fellesformat.get<XMLMottakenhetBlokk>().ediLoggId, apprec.ediloggid)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same msgId as the source`() {
        Assertions.assertEquals(fellesformat.get<XMLMsgHead>().msgInfo.msgId, apprec.msgId)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same genDate as the source`() {
        Assertions.assertEquals(
            getLocalDateTime(fellesformat.get<XMLMsgHead>().msgInfo.genDate),
            apprec.genDate
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same msgTypeVerdi as the source`() {
        Assertions.assertEquals(fellesformat.get<XMLMsgHead>().msgInfo.type.v, apprec.msgTypeVerdi)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same msgTypeBeskrivelse as the source`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.type.dn,
            apprec.msgTypeBeskrivelse
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same apprecStatusDN as the source`() {
        Assertions.assertEquals(ApprecStatus.OK.dn, apprec.apprecStatus.dn)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same apprecStatusv as the source`() {
        Assertions.assertEquals(ApprecStatus.OK.v, apprec.apprecStatus.v)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same tekstTilSykmelder as the source`() {
        Assertions.assertEquals(null, apprec.tekstTilSykmelder)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id,
            apprec.mottakerOrganisasjon.hovedIdent.id,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id typeid dn on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn,
            apprec.mottakerOrganisasjon.hovedIdent.typeId.beskrivelse,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id typeid v on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v,
            apprec.mottakerOrganisasjon.hovedIdent.typeId.verdi,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same organisationName on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName,
            apprec.mottakerOrganisasjon.navn,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id on additionalIds on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.id,
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.id,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id on additionalIds typeid dn on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.dn,
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id on additionalIds typeId v on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident?.last()?.typeId?.v,
            apprec.mottakerOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same healthcareProfessional name on the sender organisation`() {
        Assertions.assertEquals(
            "Frost Frida Perma",
            apprec.mottakerOrganisasjon.helsepersonell?.navn,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same healthcareProfessional ident on the sender organisation`() {
        Assertions.assertEquals(
            fellesformat
                .get<XMLMsgHead>()
                .msgInfo
                .sender
                .organisation
                .healthcareProfessional
                .ident
                ?.first()
                ?.id,
            apprec.mottakerOrganisasjon.helsepersonell?.hovedIdent?.id,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id typeid dn on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn,
            apprec.senderOrganisasjon.hovedIdent.typeId.beskrivelse,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id typeid v on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v,
            apprec.senderOrganisasjon.hovedIdent.typeId.verdi,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same organisationName on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName,
            apprec.senderOrganisasjon.navn,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id on additionalIds on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.id,
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.id,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id on additionalIds typeid dn on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.dn,
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.beskrivelse,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same id on additionalIds typeId v on the receiver organisation`() {
        Assertions.assertEquals(
            fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident?.last()?.typeId?.v,
            apprec.senderOrganisasjon.tilleggsIdenter?.last()?.typeId?.verdi,
        )
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same healthcareProfessional name on the receiver organisation`() {
        Assertions.assertEquals(null, apprec.senderOrganisasjon.helsepersonell?.navn)
    }

    @Test
    internal fun `Avisst AppRec with validationResult has the same healthcareProfessional ident on the receiver organisation`() {
        Assertions.assertEquals(null, apprec.senderOrganisasjon.helsepersonell?.hovedIdent?.id)
    }
}
