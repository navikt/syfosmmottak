package no.nav.syfo.apprec

import no.nav.helse.apprecV1.XMLAdditionalId
import no.nav.helse.apprecV1.XMLAppRec
import no.nav.helse.apprecV1.XMLCS
import no.nav.helse.apprecV1.XMLCV as AppRecCV
import no.nav.helse.apprecV1.XMLHCP
import no.nav.helse.apprecV1.XMLHCPerson
import no.nav.helse.apprecV1.XMLInst
import no.nav.helse.apprecV1.XMLOriginalMsgId
import no.nav.helse.msgHead.XMLCV as MsgHeadCV
import no.nav.helse.msgHead.XMLHealthcareProfessional
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLOrganisation
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.syfo.SyfoSmMottakConstant
import no.nav.syfo.apprecJaxbMarshaller
import no.nav.syfo.get
import no.nav.syfo.model.RuleInfo
import org.w3c.dom.Element
import java.time.LocalDateTime
import javax.xml.parsers.DocumentBuilderFactory

fun apprecToElement(apprec: XMLAppRec): Element {
    val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .newDocument()
    apprecJaxbMarshaller.marshal(apprec, document)
    return document.documentElement
}

fun createApprec(fellesformat: XMLEIFellesformat, apprecStatus: ApprecStatus, apprecErrors: List<AppRecCV>): XMLEIFellesformat {
    val fellesformatApprec = XMLEIFellesformat().apply {
        any.add(XMLMottakenhetBlokk().apply {
            ediLoggId = fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
            ebRole = SyfoSmMottakConstant.ebRoleNav.string
            ebService = SyfoSmMottakConstant.ebServiceSykmelding.string
            ebAction = SyfoSmMottakConstant.ebActionSvarmelding.string
        }
        )

        any.add(apprecToElement(XMLAppRec().apply {
            msgType = XMLCS().apply {
                v = SyfoSmMottakConstant.apprec.string
            }
            miGversion = SyfoSmMottakConstant.apprecVersionV1_0.string
            genDate = LocalDateTime.now()
            id = fellesformat.get<XMLMottakenhetBlokk>().ediLoggId

            sender = XMLAppRec.Sender().apply {
                hcp = fellesformat.get<XMLMsgHead>().msgInfo.receiver.organisation.intoHCP()
            }

            receiver = XMLAppRec.Receiver().apply {
                hcp = fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.intoHCP()
            }

            status = XMLCS().apply {
                v = apprecStatus.v
                dn = apprecStatus.dn
            }

            originalMsgId = XMLOriginalMsgId().apply {
                msgType = XMLCS().apply {
                    v = fellesformat.get<XMLMsgHead>().msgInfo.type.v
                    dn = fellesformat.get<XMLMsgHead>().msgInfo.type.dn
                }
                issueDate = fellesformat.get<XMLMsgHead>().msgInfo.genDate
                id = fellesformat.get<XMLMsgHead>().msgInfo.msgId
            }

            error.addAll(apprecErrors)
        }))
    }

    return fellesformatApprec
}

fun XMLHealthcareProfessional.intoHCPerson(): XMLHCPerson = XMLHCPerson().apply {
    name = if (middleName == null) "$familyName $givenName" else "$familyName $givenName $middleName"
    id = ident.first().id
    typeId = ident.first().typeId.intoCS()
    additionalId += ident.drop(1)
}

fun XMLOrganisation.intoHCP(): XMLHCP = XMLHCP().apply {
    inst = ident.first().intoInst().apply {
        name = organisationName
        additionalId += ident.drop(1)

        if (healthcareProfessional != null) {
            hcPerson += healthcareProfessional.intoHCPerson()
        }
    }
}

fun XMLIdent.intoInst(): XMLInst {
    val ident = this
    return XMLInst().apply {
        id = ident.id
        typeId = ident.typeId.intoCS()
    }
}

fun MsgHeadCV.intoCS(): XMLCS {
    val msgHeadCV = this
    return XMLCS().apply {
        dn = msgHeadCV.dn
        v = msgHeadCV.v
    }
}

operator fun MutableList<XMLAdditionalId>.plusAssign(idents: Iterable<XMLIdent>) {
    this.addAll(idents.map { it.intoAdditionalId() })
}

fun XMLIdent.intoAdditionalId(): XMLAdditionalId {
    val ident = this
    return XMLAdditionalId().apply {
        id = ident.id
        type = XMLCS().apply {
            dn = ident.typeId.dn
            v = ident.typeId.v
        }
    }
}

fun RuleInfo.toApprecCV(): AppRecCV {
    val ruleInfo = this
    return createApprecError(ruleInfo.messageForSender)
}

fun createApprecError(textToTreater: String): AppRecCV = AppRecCV().apply {
        dn = textToTreater
        v = "2.16.578.1.12.4.1.1.8221"
        s = "X99"
    }
