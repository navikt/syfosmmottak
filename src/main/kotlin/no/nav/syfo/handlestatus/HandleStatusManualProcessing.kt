package no.nav.syfo.handlestatus

import javax.jms.MessageProducer
import javax.jms.Session
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.fetchBehandlendeEnhet
import no.nav.syfo.fetchGeografiskTilknytning
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendValidationResult
import no.nav.syfo.service.fetchDiskresjonsKode
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter

suspend fun handleStatusMANUALPROCESSING(
    personV3: PersonV3,
    receivedSykmelding: ReceivedSykmelding,
    arbeidsfordelingV1: ArbeidsfordelingV1,
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
    sm2013BehandlingsUtfallToipic: String
) {
    val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
    val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
    val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode)
    if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
        log.error("arbeidsfordeling fant ingen nav-enheter {}", StructuredArguments.fields(loggingMeta))
    }
    val behandlendeEnhet = finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId
            ?: NAV_OPPFOLGING_UTLAND_KONTOR_NR

    createTask(kafkaManuelTaskProducer, receivedSykmelding, validationResult, behandlendeEnhet, loggingMeta)

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

fun createTask(
        kafkaProducer: KafkaProducer<String, ProduceTask>,
        receivedSykmelding: ReceivedSykmelding,
        results: ValidationResult,
        navKontor: String,
        loggingMeta: LoggingMeta
) {
    kafkaProducer.send(
            ProducerRecord(
                    "aapen-syfo-oppgave-produserOppgave",
                    receivedSykmelding.sykmelding.id,
                    ProduceTask().apply {
                        messageId = receivedSykmelding.msgId
                        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
                        tildeltEnhetsnr = navKontor
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