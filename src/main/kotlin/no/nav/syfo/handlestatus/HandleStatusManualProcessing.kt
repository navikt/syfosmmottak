package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.log
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendReceivedSykmelding
import no.nav.syfo.sendValidationResult
import no.nav.syfo.service.notifySyfoService
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.jms.MessageProducer
import javax.jms.Session

fun handleStatusMANUALPROCESSING(
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
    sm2013BehandlingsUtfallTopic: String,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    syfoSmManuellTopic: String
) {
    val sendToSyfosmManuell = sendToSyfosmManuell(ruleHits = validationResult.ruleHits)

    if (sendToSyfosmManuell) {
        log.info("Sending manuell oppgave to syfosmmanuell-backend {}", StructuredArguments.fields(loggingMeta))
        val apprec = fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.OK,
            null,
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation,
            msgHead.msgInfo.genDate
        )
        sendManuellTask(receivedSykmelding, validationResult, apprec, syfoSmManuellTopic, kafkaproducerManuellOppgave)
    } else {
        log.info("Sending manuell oppgave to syfosmoppgave {}", StructuredArguments.fields(loggingMeta))
        opprettOppgave(kafkaManuelTaskProducer, receivedSykmelding, validationResult, loggingMeta)

        sendReceivedSykmelding(sm2013ManualHandlingTopic, receivedSykmelding, kafkaproducerreceivedSykmelding)

        sendValidationResult(validationResult, kafkaproducervalidationResult, sm2013BehandlingsUtfallTopic, receivedSykmelding, loggingMeta)

        val apprec = fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.OK,
            null,
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation,
            msgHead.msgInfo.genDate
        )
        sendReceipt(apprec, sm2013ApprecTopic, kafkaproducerApprec)
        log.info("Apprec receipt sent to kafka topic {}, {}", sm2013ApprecTopic, StructuredArguments.fields(loggingMeta))

        notifySyfoService(
            session = session, receiptProducer = syfoserviceProducer, ediLoggId = ediLoggId,
            sykmeldingId = receivedSykmelding.sykmelding.id, msgId = msgId, healthInformation = healthInformation
        )
        log.info("Message send to syfoService {}, {}", syfoserviceQueueName, StructuredArguments.fields(loggingMeta))
    }
}

fun opprettOppgave(
    kafkaProducer: KafkaProducer<String, ProduceTask>,
    receivedSykmelding: ReceivedSykmelding,
    results: ValidationResult,
    loggingMeta: LoggingMeta
) {
    try {
        kafkaProducer.send(
            ProducerRecord(
                "aapen-syfo-oppgave-produserOppgave",
                receivedSykmelding.sykmelding.id,
                opprettProduceTask(receivedSykmelding, results, loggingMeta)
            )
        ).get()
        log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave {}", StructuredArguments.fields(loggingMeta))
    } catch (ex: Exception) {
        log.error("Failed to send producer task for sykmelding {} to kafka", receivedSykmelding.sykmelding.id)
        throw ex
    }
}

fun opprettProduceTask(receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, loggingMeta: LoggingMeta): ProduceTask {
    val oppgave = ProduceTask().apply {
        messageId = receivedSykmelding.msgId
        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
        tildeltEnhetsnr = ""
        opprettetAvEnhetsnr = "9999"
        behandlesAvApplikasjon = "FS22" // Gosys
        orgnr = receivedSykmelding.legekontorOrgNr ?: ""
        beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${validationResult.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"
        temagruppe = "ANY"
        tema = "SYM"
        behandlingstema = "ANY"
        oppgavetype = "BEH_EL_SYM"
        behandlingstype = "ANY"
        mappeId = 1
        aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
        fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4)))
        prioritet = PrioritetType.NORM
        metadata = mapOf()
    }
    if (validationResult.ruleHits.find { it.ruleName == "SYKMELDING_MED_BEHANDLINGSDAGER" } != null) {
        log.info("Sykmelding inneholder behandlingsdager, {}", StructuredArguments.fields(loggingMeta))
        oppgave.behandlingstema = "ab0351"
    }
    if (validationResult.ruleHits.find { it.ruleName == "SYKMELDING_MED_REISETILSKUDD" } != null) {
        log.info("Sykmelding inneholder reisetilskudd, {}", StructuredArguments.fields(loggingMeta))
        oppgave.behandlingstema = "ab0237"
    }
    return oppgave
}

fun sendManuellTask(
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    apprec: Apprec,
    sm2013ManeullTopic: String,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>
) {
    try {
        val manuellOppgave = ManuellOppgave(
            receivedSykmelding,
            validationResult,
            apprec
        )
        kafkaproducerManuellOppgave.send(ProducerRecord(sm2013ManeullTopic, manuellOppgave)).get()
    } catch (ex: Exception) {
        log.error("Failed to send manuell oppgave for sykmelding {} to kafka", receivedSykmelding.sykmelding.id)
        throw ex
    }
}

fun sendToSyfosmManuell(ruleHits: List<RuleInfo>): Boolean {
    return ruleHits.find { it.ruleName == "SYKMELDING_MED_BEHANDLINGSDAGER" || it.ruleName == "SYKMELDING_MED_REISETILSKUDD" } == null
}

fun finnFristForFerdigstillingAvOppgave(ferdistilleDato: LocalDate): LocalDate {
    return setToWorkDay(ferdistilleDato)
}

fun setToWorkDay(ferdistilleDato: LocalDate): LocalDate =
    when (ferdistilleDato.dayOfWeek) {
        DayOfWeek.SATURDAY -> ferdistilleDato.plusDays(2)
        DayOfWeek.SUNDAY -> ferdistilleDato.plusDays(1)
        else -> ferdistilleDato
    }
