package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.bootstrap.HttpClients
import no.nav.syfo.bootstrap.KafkaClients
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.db.Database
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.MqTlsUtils
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.service.DuplicationService
import no.nav.syfo.service.VirusScanService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.vedlegg.google.BucketUploadService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.FileInputStream
import javax.jms.Session

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmmottak")

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val database = Database(env)

    val serviceUser = VaultServiceUser()
    MqTlsUtils.getMqTlsConfig().forEach { key, value -> System.setProperty(key as String, value as String) }
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    DefaultExports.initialize()

    val httpClients = HttpClients(env)
    val kafkaClients = KafkaClients(env)

    val sykmeldingVedleggStorageCredentials: Credentials = GoogleCredentials.fromStream(FileInputStream("/var/run/secrets/sykmeldingvedlegg-google-creds.json"))
    val sykmeldingVedleggStorage: Storage = StorageOptions.newBuilder().setCredentials(sykmeldingVedleggStorageCredentials).build().service
    val bucketUploadService = BucketUploadService(env.sykmeldingVedleggBucketName, sykmeldingVedleggStorage)
    val virusScanService = VirusScanService(httpClients.clamAvClient)

    val duplicationService = DuplicationService(database)

    launchListeners(
        env, applicationState,
        httpClients.emottakSubscriptionClient, kafkaClients.kafkaProducerReceivedSykmelding,
        kafkaClients.kafkaProducerValidationResult,
        httpClients.syfoSykemeldingRuleClient, httpClients.sarClient, httpClients.pdlPersonService,
        serviceUser, kafkaClients.manualValidationKafkaProducer,
        kafkaClients.kafkaProducerApprec, kafkaClients.kafkaproducerManuellOppgave,
        httpClients.norskHelsenettClient, bucketUploadService, virusScanService, duplicationService
    )

    applicationServer.start()
}

@DelicateCoroutinesApi
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter", e.cause)
        } finally {
            applicationState.ready = false
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    emottakSubscriptionClient: EmottakSubscriptionClient,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    kuhrSarClient: SarClient,
    pdlPersonService: PdlPersonService,
    serviceUser: VaultServiceUser,
    kafkaManuelTaskProducer: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    norskHelsenettClient: NorskHelsenettClient,
    bucketUploadService: BucketUploadService,
    virusScanService: VirusScanService,
    duplicationService: DuplicationService
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(serviceUser.serviceuserUsername, serviceUser.serviceuserPassword).use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

            val inputconsumer = session.consumerForQueue(env.inputQueueName)
            val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)

            BlockingApplicationRunner(
                env,
                applicationState,
                emottakSubscriptionClient,
                syfoSykemeldingRuleClient,
                norskHelsenettClient,
                kuhrSarClient,
                pdlPersonService,
                bucketUploadService,
                kafkaproducerreceivedSykmelding,
                kafkaproducervalidationResult,
                kafkaManuelTaskProducer,
                kafkaproducerApprec,
                kafkaproducerManuellOppgave,
                virusScanService,
                duplicationService
            ).run(
                inputconsumer,
                backoutProducer
            )
        }
    }
}

fun sendReceipt(
    apprec: Apprec,
    apprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    loggingMeta: LoggingMeta
) {
    try {
        kafkaproducerApprec.send(ProducerRecord(apprecTopic, apprec)).get()
        log.info("Apprec receipt sent to kafka topic $apprecTopic, {}", fields(loggingMeta))
    } catch (ex: Exception) {
        log.error("failed to send apprec to kafka {}", fields(loggingMeta))
        throw ex
    }
}

fun sendValidationResult(
    validationResult: ValidationResult,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    behandlingsUtfallTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta
) {

    try {
        kafkaproducervalidationResult.send(
            ProducerRecord(behandlingsUtfallTopic, receivedSykmelding.sykmelding.id, validationResult)
        ).get()
        log.info("Validation results send to kafka {}, {}", behandlingsUtfallTopic, fields(loggingMeta))
    } catch (ex: Exception) {
        log.error("failed to send validation result for sykmelding {}", receivedSykmelding.sykmelding.id)
        throw ex
    }
}

fun sendReceivedSykmelding(
    receivedSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>
) {
    try {
        kafkaproducerreceivedSykmelding.send(
            ProducerRecord(receivedSykmeldingTopic, receivedSykmelding.sykmelding.id, receivedSykmelding)
        ).get()
        log.info("Sykmelding sendt to kafka topic {} sykmelding id {}", receivedSykmeldingTopic, receivedSykmelding.sykmelding.id)
    } catch (ex: Exception) {
        log.error("failed to send sykmelding to kafka result for sykmelding {}", receivedSykmelding.sykmelding.id)
        throw ex
    }
}
