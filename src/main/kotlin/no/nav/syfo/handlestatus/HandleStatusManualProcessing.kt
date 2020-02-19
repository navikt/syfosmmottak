package no.nav.syfo.handlestatus

import com.ctc.wstx.exc.WstxException
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendValidationResult
import no.nav.syfo.service.fetchDiskresjonsKode
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Diskresjonskoder
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Oppgavetyper
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeRequest
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

suspend fun handleStatusMANUALPROCESSING(
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    sm2013ApprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    session: Session,
    syfoserviceProducer: MessageProducer,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    syfoserviceQueueName: String,
    validationResult: ValidationResult,
    kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    sm2013ManualHandlingTopic: String,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013BehandlingsUtfallToipic: String,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    syfoSmManuellTopic: String,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1
) {

    val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
    val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
    val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode)
    if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
        log.error("arbeidsfordeling fant ingen nav-enheter {}", StructuredArguments.fields(loggingMeta))
    }
    val behandlendeEnhet = finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId
            ?: "0393"

    val sendToSyfosmManuell = sendToSyfosmManuell(validationResult.ruleHits, behandlendeEnhet)

    if (sendToSyfosmManuell) {
        log.info("Sending manuell oppgave to syfosmmanuell-backend")
        val apprec = fellesformat.toApprec(
                ediLoggId,
                msgId,
                msgHead,
                ApprecStatus.OK,
                null,
                msgHead.msgInfo.receiver.organisation,
                msgHead.msgInfo.sender.organisation
        )
        sendManuellTask(receivedSykmelding, validationResult, apprec, syfoSmManuellTopic, kafkaproducerManuellOppgave)
    } else {
        log.info("Sending manuell oppgave to syfosmoppgave")
        opprettOppgave(kafkaManuelTaskProducer, receivedSykmelding, validationResult, loggingMeta)

        notifySyfoService(session = session, receiptProducer = syfoserviceProducer, ediLoggId = ediLoggId,
                sykmeldingId = receivedSykmelding.sykmelding.id, msgId = msgId, healthInformation = healthInformation)
        log.info("Message send to syfoService {}, {}", syfoserviceQueueName, StructuredArguments.fields(loggingMeta))

        kafkaproducerreceivedSykmelding.send(ProducerRecord(sm2013ManualHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
        log.info("Message send to kafka {}, {}", sm2013ManualHandlingTopic, StructuredArguments.fields(loggingMeta))

        sendValidationResult(validationResult, kafkaproducervalidationResult, sm2013BehandlingsUtfallToipic, receivedSykmelding, loggingMeta)

        val apprec = fellesformat.toApprec(
                ediLoggId,
                msgId,
                msgHead,
                ApprecStatus.OK,
                null,
                msgHead.msgInfo.receiver.organisation,
                msgHead.msgInfo.sender.organisation
        )
        sendReceipt(apprec, sm2013ApprecTopic, kafkaproducerApprec)
        log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopic, StructuredArguments.fields(loggingMeta))
    }
}

fun opprettOppgave(
    kafkaProducer: KafkaProducer<String, ProduceTask>,
    receivedSykmelding: ReceivedSykmelding,
    results: ValidationResult,
    loggingMeta: LoggingMeta
) {
    kafkaProducer.send(
            ProducerRecord(
                    "aapen-syfo-oppgave-produserOppgave",
                    receivedSykmelding.sykmelding.id,
                    ProduceTask().apply {
                        messageId = receivedSykmelding.msgId
                        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
                        tildeltEnhetsnr = ""
                        opprettetAvEnhetsnr = "9999"
                        behandlesAvApplikasjon = "FS22" // Gosys
                        orgnr = receivedSykmelding.legekontorOrgNr ?: ""
                        beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${results.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"
                        temagruppe = "ANY"
                        tema = "SYM"
                        behandlingstema = "ANY"
                        oppgavetype = "BEH_EL_SYM"
                        behandlingstype = "ANY"
                        mappeId = 1
                        aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                        fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
                        prioritet = PrioritetType.NORM
                        metadata = mapOf()
                    }))

    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave {}", StructuredArguments.fields(loggingMeta))
}

fun sendManuellTask(
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    apprec: Apprec,
    sm2013ManeullTopic: String,
    kafkaproducerApprec: KafkaProducer<String, ManuellOppgave>
) {
    val manuellOppgave = ManuellOppgave(
            receivedSykmelding,
            validationResult,
            apprec)
    kafkaproducerApprec.send(ProducerRecord(sm2013ManeullTopic, manuellOppgave))
}

suspend fun fetchGeografiskTilknytning(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): HentGeografiskTilknytningResponse =
        retry(callName = "tps_hent_geografisktilknytning",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                    NorskIdent()
                            .withIdent(receivedSykmelding.personNrPasient)
                            .withType(Personidenter().withValue("FNR")))))
        }

suspend fun fetchBehandlendeEnhet(arbeidsfordelingV1: ArbeidsfordelingV1, geografiskTilknytning: GeografiskTilknytning?, patientDiskresjonsKode: String?): FinnBehandlendeEnhetListeResponse? =
        retry(callName = "finn_nav_kontor",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            arbeidsfordelingV1.finnBehandlendeEnhetListe(FinnBehandlendeEnhetListeRequest().apply {
                val afk = ArbeidsfordelingKriterier()
                if (geografiskTilknytning?.geografiskTilknytning != null) {
                    afk.geografiskTilknytning = Geografi().apply {
                        value = geografiskTilknytning.geografiskTilknytning
                    }
                }
                afk.tema = Tema().apply {
                    value = "SYM"
                }

                afk.oppgavetype = Oppgavetyper().apply {
                    value = "BEH_EL_SYM"
                }

                if (!patientDiskresjonsKode.isNullOrBlank()) {
                    afk.diskresjonskode = Diskresjonskoder().apply {
                        value = patientDiskresjonsKode
                    }
                }

                arbeidsfordelingKriterier = afk
            })
        }

fun sendToSyfosmManuell(ruleHits: List<RuleInfo>, behandlendeEnhet: String): Boolean =
        ruleHits.find { it.ruleName == "PASIENTEN_HAR_KODE_6" || it.ruleName == "BEHANDLER_KI_FT_MT_BENYTTER_ANNEN_DIAGNOSEKODE_ENN_L" } == null &&
                pilotBehandleneEnhet(behandlendeEnhet)

fun pilotBehandleneEnhet(behandlendeEnhet: String): Boolean =
        listOf("0415", "0412", "0403", "0417", "1101","1108", "1102", "1129", "1106",
                "1111", "1112","1119","1120","1122","1124","1127","1130","1133","1134",
                "1135","1146","1149","1151","1160","1161","1162","1164","1165","1169", "1167", "1168")
                .contains(behandlendeEnhet)
