package no.nav.syfo.handlestatus

import com.ctc.wstx.exc.WstxException
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.bootstrap.KafkaClients
import no.nav.syfo.client.ArbeidsFordelingClient
import no.nav.syfo.client.ArbeidsfordelingRequest
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
import no.nav.syfo.service.fetchEgenAnsatt
import no.nav.syfo.util.LoggingMeta
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

@KtorExperimentalAPI
suspend fun handleStatusMANUALPROCESSING(
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    sm2013ApprecTopic: String,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    validationResult: ValidationResult,
    sm2013ManualHandlingTopic: String,
    sm2013BehandlingsUtfallTopic: String,
    syfoSmManuellTopic: String,
    personV3: PersonV3,
    egenAnsattV1: EgenAnsattV1,
    arbeidsFordelingClient: ArbeidsFordelingClient,
    kafkaClients: KafkaClients
) {

    val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
    val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
    val egenAnsatt = fetchEgenAnsatt(egenAnsattV1, receivedSykmelding)

    val arbeidsfordelingRequest = ArbeidsfordelingRequest(
            tema = "SYM",
            geografiskOmraade = geografiskTilknytning?.geografiskTilknytning?.geografiskTilknytning ?: null,
            behandlingstema = "ANY",
            behandlingstype = "ANY",
            oppgavetype = "BEH_EL_SYM",
            diskresjonskode = patientDiskresjonsKode,
            skjermet = egenAnsatt

    )

    val arbeidsfordelingResponse = arbeidsFordelingClient.finnBehandlendeEnhet(arbeidsfordelingRequest)

    if (arbeidsfordelingResponse?.firstOrNull()?.enhetId == null) {
        log.warn("arbeidsfordeling fant ingen nav-enheter {}", StructuredArguments.fields(loggingMeta))
    }
    val behandlendeEnhet = arbeidsfordelingResponse?.firstOrNull()?.enhetNr
            ?: "0393"

    log.info("BehandlendeEnhet er: $behandlendeEnhet {}", StructuredArguments.fields(loggingMeta))

    val sendToSyfosmManuell = sendToSyfosmManuell(validationResult.ruleHits, behandlendeEnhet)

    if (sendToSyfosmManuell && !egenAnsatt) {
        log.info("Sending manuell oppgave to syfosmmanuell-backend {}", StructuredArguments.fields(loggingMeta))
        val apprec = toApprec(
                ediLoggId,
                msgId,
                msgHead,
                ApprecStatus.OK,
                null,
                msgHead.msgInfo.receiver.organisation,
                msgHead.msgInfo.sender.organisation
        )
        sendManuellTask(receivedSykmelding, validationResult, apprec, syfoSmManuellTopic, kafkaClients.kafkaproducerManuellOppgave)
    } else {
        log.info("Sending manuell oppgave to syfosmoppgave {}", StructuredArguments.fields(loggingMeta))
        opprettOppgave(kafkaClients.manualValidationKafkaProducer, receivedSykmelding, validationResult, loggingMeta)

        kafkaClients.syfoserviceKafkaProducer.publishSykmeldingToKafka(sykmeldingId = receivedSykmelding.sykmelding.id, helseOpplysningerArbeidsuforhet = healthInformation)

        log.info("Message send to syfoservice-mq-producer {}", StructuredArguments.fields(loggingMeta))

        kafkaClients.kafkaProducerReceivedSykmelding.send(ProducerRecord(sm2013ManualHandlingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding))
        log.info("Message send to kafka {}, {}", sm2013ManualHandlingTopic, StructuredArguments.fields(loggingMeta))

        sendValidationResult(validationResult, kafkaClients.kafkaProducerValidationResult, sm2013BehandlingsUtfallTopic, receivedSykmelding, loggingMeta)

        val apprec = toApprec(
                ediLoggId,
                msgId,
                msgHead,
                ApprecStatus.OK,
                null,
                msgHead.msgInfo.receiver.organisation,
                msgHead.msgInfo.sender.organisation
        )
        sendReceipt(apprec, sm2013ApprecTopic, kafkaClients.kafkaProducerApprec)
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
                        fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(finnFristForFerdigstillingAvOppgave(LocalDate.now().plusDays(4)))
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

fun sendToSyfosmManuell(ruleHits: List<RuleInfo>, behandlendeEnhet: String): Boolean =
        ruleHits.find { it.ruleName == "PASIENTEN_HAR_KODE_6" } == null &&
                pilotBehandleneEnhet(behandlendeEnhet)

fun pilotBehandleneEnhet(behandlendeEnhet: String): Boolean =
        listOf("0415", "0412", "0403", "0417", "1101", "1108", "1102", "1129", "1106",
                "1111", "1112", "1119", "1120", "1122", "1124", "1127", "1130", "1133", "1134",
                "1135", "1146", "1149", "1151", "1160", "1161", "1162", "1164", "1165", "1169", "1167", "1168")
                .contains(behandlendeEnhet)

fun finnFristForFerdigstillingAvOppgave(ferdistilleDato: LocalDate): LocalDate {
    return setToWorkDay(ferdistilleDato)
}

fun setToWorkDay(ferdistilleDato: LocalDate): LocalDate =
        when (ferdistilleDato.dayOfWeek) {
            DayOfWeek.SATURDAY -> ferdistilleDato.plusDays(2)
            DayOfWeek.SUNDAY -> ferdistilleDato.plusDays(1)
            else -> ferdistilleDato
        }
