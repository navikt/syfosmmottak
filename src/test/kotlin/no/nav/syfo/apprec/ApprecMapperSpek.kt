package no.nav.syfo.apprec

import no.kith.xmlstds.apprec._2004_11_21.XMLAppRec
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.syfo.SyfoMottakConstant
import no.nav.syfo.fellesformatUnmarshaller
import no.nav.syfo.get
import no.nav.syfo.utils.getFileAsString
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader
import java.time.LocalDateTime

object ApprecMapperSpek : Spek({
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat

    describe("Duplicate AppRec") {
        val ff = createApprec(fellesformat, ApprecStatus.avvist)
        ff.get<XMLAppRec>().error.add(mapApprecErrorToAppRecCV(ApprecError.DUPLICATE))
        it("Has the same ediLoggId as the source") {
            ff.get<XMLMottakenhetBlokk>().ediLoggId shouldEqual fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
        }
        it("Sets appRec status dn to Avvist") {
            ff.get<XMLAppRec>().status.dn shouldEqual ApprecStatus.avvist.dn
        }
        it("Sets appRec error dn to duplicate") {
            ff.get<XMLAppRec>().error.first().dn shouldEqual ApprecError.DUPLICATE.dn
        }
        it("Sets appRec error v to duplicate") {
            ff.get<XMLAppRec>().error.first().v shouldEqual ApprecError.DUPLICATE.v
        }
        it("Sets appRec error s to duplicate") {
            ff.get<XMLAppRec>().error.first().s shouldEqual ApprecError.DUPLICATE.s
        }
    }

    describe("OK AppRec") {
        val ff = createApprec(fellesformat, ApprecStatus.ok)
        it("Sets ebRole to ebRoleNav") {
            ff.get<XMLMottakenhetBlokk>().ebRole shouldEqual SyfoMottakConstant.ebRoleNav.string
        }
        it("Sets ebService") {
            ff.get<XMLMottakenhetBlokk>().ebService shouldEqual SyfoMottakConstant.ebServiceLegemelding.string
        }
        it("Sets ebAction") {
            ff.get<XMLMottakenhetBlokk>().ebAction shouldEqual SyfoMottakConstant.ebActionSvarmelding.string
        }
        it("Sets appRec message type") {
            ff.get<XMLAppRec>().msgType.v shouldEqual SyfoMottakConstant.APPREC.string
        }
        it("Sets appRec miGversion") {
            ff.get<XMLAppRec>().miGversion shouldEqual SyfoMottakConstant.APPRECVersionV1_0.string
        }
        it("Sets genDate to current date") {
            val now = LocalDateTime.now()
            ff.get<XMLAppRec>().genDate.monthValue shouldEqual now.monthValue
            ff.get<XMLAppRec>().genDate.dayOfMonth shouldEqual now.dayOfMonth
            ff.get<XMLAppRec>().genDate.hour shouldEqual now.hour
        }
        it("Sets appRec id to ediLoggId") {
            ff.get<XMLAppRec>().id shouldEqual fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
        }
        it("Sets senders appRec sender institution name to receiver organizationName") {
            ff.get<XMLAppRec>().sender.hcp.inst.name shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.organisationName
        }
        it("Sets senders appRec institution id to first organization ident id") {
            ff.get<XMLAppRec>().sender.hcp.inst.id shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().id
        }
        it("Sets senders appRec institution typeId dn to first organization ident typeId dn") {
            ff.get<XMLAppRec>().sender.hcp.inst.typeId.dn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.dn
        }
        it("Sets senders appRec institution typeId v to first organization ident typeId v") {
            ff.get<XMLAppRec>().sender.hcp.inst.typeId.v shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident.first().typeId.v
        }
        it("Sets senders first additional appRec institution id to second organization ident id") {
            ff.get<XMLAppRec>().sender.hcp.inst.additionalId.first().id shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident[1].id
        }
        it("Sets senders first additional appRec institution typeId dn to second organization ident typeId dn") {
            ff.get<XMLAppRec>().sender.hcp.inst.additionalId.first().type.dn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident[1].typeId.dn
        }
        it("Sets senders first additional appRec institution typeId v to second organization ident typeId v") {
            ff.get<XMLAppRec>().sender.hcp.inst.additionalId.first().type.v shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.ident[1].typeId.v
        }
        it("Sets receivers appRec institution name to sender organizationName") {
            ff.get<XMLAppRec>().receiver.hcp.inst.name shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.organisationName
        }
        it("Sets receivers appRec institution id to first sender organization ident id") {
            ff.get<XMLAppRec>().receiver.hcp.inst.id shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().id
        }
        it("Sets receivers appRec institution typeId dn to first sender organization ident typeId dn") {
            ff.get<XMLAppRec>().receiver.hcp.inst.typeId.dn shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.dn
        }
        it("Sets receivers appRec institution typeId v to first organization ident typeId v") {
            ff.get<XMLAppRec>().receiver.hcp.inst.typeId.v shouldEqual
                    fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.first().typeId.v
        }

        it("Sets appRec status dn to OK") {
            ff.get<XMLAppRec>().status.dn shouldEqual ApprecStatus.ok.dn
        }
        it("Sets appRec status v to OK") {
            ff.get<XMLAppRec>().status.v shouldEqual ApprecStatus.ok.v
        }
        it("Sets appRec originalMsgId") {
            ff.get<XMLAppRec>().originalMsgId.msgType.dn shouldEqual "Medisinsk vurdering av arbeidsmulighet ved sykdom, sykmelding"
        }
        it("Sets appRec originalMsgId") {
            ff.get<XMLAppRec>().originalMsgId.msgType.v shouldEqual "SYKMELD"
        }
        it("Sets appRec genDate as issueDate") {
            ff.get<XMLAppRec>().originalMsgId.issueDate shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.genDate
        }
        it("Sets appRec originalMsgId to msgId") {
            ff.get<XMLAppRec>().originalMsgId.id shouldEqual fellesformat.get<XMLMsgHead>().msgInfo.msgId
        }
    }
    describe("Error AppRec") {
        val ff = createApprec(fellesformat, ApprecStatus.avvist)
        ff.get<XMLAppRec>().error.add(mapApprecErrorToAppRecCV(ApprecError.BEHANDLER_PERSON_NUMBER_NOT_VALID))
        it("Sets appRec error dn to duplicate") {
            ff.get<XMLAppRec>().error.first().dn shouldEqual ApprecError.BEHANDLER_PERSON_NUMBER_NOT_VALID.dn
        }
        it("Sets appRec error v to duplicate") {
            ff.get<XMLAppRec>().error.first().v shouldEqual ApprecError.BEHANDLER_PERSON_NUMBER_NOT_VALID.v
        }
        it("Sets appRec error s to duplicate") {
            ff.get<XMLAppRec>().error.first().s shouldEqual ApprecError.BEHANDLER_PERSON_NUMBER_NOT_VALID.s
        }
    }

})
