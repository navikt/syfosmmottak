package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.NAV_OPPFOLGING_UTLAND_KONTOR_NR
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.createTask
import no.nav.syfo.fetchBehandlendeEnhet
import no.nav.syfo.fetchDiskresjonsKode
import no.nav.syfo.fetchGeografiskTilknytning
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sendReceipt
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import javax.jms.MessageProducer
import javax.jms.Session

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
        sm2013ManualHandlingTopic: String
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