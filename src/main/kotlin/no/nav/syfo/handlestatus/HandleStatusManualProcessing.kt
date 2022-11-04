package no.nav.syfo.handlestatus

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.log
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.PrioritetType
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sendReceipt
import no.nav.syfo.sendReceivedSykmelding
import no.nav.syfo.sendValidationResult
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.get
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun handleStatusMANUALPROCESSING(
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    apprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    validationResult: ValidationResult,
    kafkaManuelTaskProducer: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    manuellBehandlingSykmeldingTopic: String,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    behandlingsUtfallTopic: String,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    syfoSmManuellTopic: String,
    produserOppgaveTopic: String
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
            msgHead.msgInfo.genDate,
            null,
            fellesformat.get<XMLMottakenhetBlokk>().ebService
        )
        sendManuellTask(receivedSykmelding, validationResult, apprec, syfoSmManuellTopic, kafkaproducerManuellOppgave)
    } else {
        log.info("Sending manuell oppgave to syfosmoppgave {}", StructuredArguments.fields(loggingMeta))
        opprettOppgave(kafkaManuelTaskProducer, receivedSykmelding, validationResult, produserOppgaveTopic, loggingMeta)

        sendReceivedSykmelding(manuellBehandlingSykmeldingTopic, receivedSykmelding, kafkaproducerreceivedSykmelding)

        sendValidationResult(validationResult, kafkaproducervalidationResult, behandlingsUtfallTopic, receivedSykmelding, loggingMeta)

        val apprec = fellesformat.toApprec(
            ediLoggId,
            msgId,
            msgHead,
            ApprecStatus.OK,
            null,
            msgHead.msgInfo.receiver.organisation,
            msgHead.msgInfo.sender.organisation,
            msgHead.msgInfo.genDate,
            null,
            fellesformat.get<XMLMottakenhetBlokk>().ebService
        )
        sendReceipt(apprec, apprecTopic, kafkaproducerApprec, loggingMeta)
        log.info("Apprec receipt sent to kafka topic {}, {}", apprecTopic, StructuredArguments.fields(loggingMeta))
    }
}

fun opprettOppgave(
    kafkaProducer: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    receivedSykmelding: ReceivedSykmelding,
    results: ValidationResult,
    produserOppgaveTopic: String,
    loggingMeta: LoggingMeta
) {
    try {
        kafkaProducer.send(
            ProducerRecord(
                produserOppgaveTopic,
                receivedSykmelding.sykmelding.id,
                opprettOpprettOppgaveKafkaMessage(receivedSykmelding, results, loggingMeta)
            )
        ).get()
        log.info("Message sendt to topic: $produserOppgaveTopic {}", StructuredArguments.fields(loggingMeta))
    } catch (ex: Exception) {
        log.error("Failed to send producer task for sykmelding {} to kafka", receivedSykmelding.sykmelding.id)
        throw ex
    }
}

fun opprettOpprettOppgaveKafkaMessage(receivedSykmelding: ReceivedSykmelding, validationResult: ValidationResult, loggingMeta: LoggingMeta): OpprettOppgaveKafkaMessage {
    val oppgave = OpprettOppgaveKafkaMessage(
        messageId = receivedSykmelding.msgId,
        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId,
        tildeltEnhetsnr = "",
        opprettetAvEnhetsnr = "9999",
        behandlesAvApplikasjon = "FS22", // Gosys
        orgnr = receivedSykmelding.legekontorOrgNr ?: "",
        beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${validationResult.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}",
        temagruppe = "ANY",
        tema = "SYM",
        behandlingstema = if (validationResult.ruleHits.find { it.ruleName == "SYKMELDING_MED_BEHANDLINGSDAGER" } != null) {
            log.info("Sykmelding inneholder behandlingsdager, {}", StructuredArguments.fields(loggingMeta))
            "ab0351"
        } else {
            "ANY"
        },
        oppgavetype = "BEH_EL_SYM",
        behandlingstype = "ANY",
        mappeId = 1,
        aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now()),
        fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4))),
        prioritet = PrioritetType.NORM,
        metadata = mapOf()
    )

    return oppgave
}

fun sendManuellTask(
    receivedSykmelding: ReceivedSykmelding,
    validationResult: ValidationResult,
    apprec: Apprec,
    syfoSmManuellTopic: String,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>
) {
    try {
        val manuellOppgave = ManuellOppgave(
            receivedSykmelding,
            validationResult,
            apprec
        )
        kafkaproducerManuellOppgave.send(ProducerRecord(syfoSmManuellTopic, receivedSykmelding.sykmelding.id, manuellOppgave)).get()
    } catch (ex: Exception) {
        log.error("Failed to send manuell oppgave for sykmelding {} to kafka", receivedSykmelding.sykmelding.id)
        throw ex
    }
}

fun sendToSyfosmManuell(ruleHits: List<RuleInfo>): Boolean {
    return ruleHits.find { it.ruleName == "SYKMELDING_MED_BEHANDLINGSDAGER" } == null
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
