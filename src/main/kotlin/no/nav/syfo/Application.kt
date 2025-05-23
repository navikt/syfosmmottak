package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import io.ktor.http.headers
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.prometheus.client.hotspot.DefaultExports
import jakarta.jms.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.bootstrap.HttpClients
import no.nav.syfo.bootstrap.KafkaClients
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.db.Database
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.MqTlsUtils
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.plugins.configureLifecycleHooks
import no.nav.syfo.plugins.configureRouting
import no.nav.syfo.service.DuplicationService
import no.nav.syfo.service.UploadSykmeldingService
import no.nav.syfo.service.VirusScanService
import no.nav.syfo.unleash.getUnleash
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.vedlegg.google.BucketUploadService
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val PROCESSING_TARGET_HEADER = "processing-target"
const val TSM_PROCESSING_TARGET_VALUE = "tsm"

val objectMapper: ObjectMapper =
    ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

val logger: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmmottak")

fun main() {
    val embeddedServer =
        embeddedServer(
            Netty,
            port = EnvironmentVariables().applicationPort,
            module = Application::module,
        )
    embeddedServer.start(true)
}

@OptIn(DelicateCoroutinesApi::class)
fun Application.module() {
    val environmentVariables = EnvironmentVariables()
    val applicationState = ApplicationState()
    val database = Database(environmentVariables)

    val applicationServiceUser = ApplicationServiceUser()

    MqTlsUtils.getMqTlsConfig().forEach { key, value ->
        System.setProperty(key as String, value as String)
    }

    DefaultExports.initialize()

    configureLifecycleHooks(applicationState = applicationState)
    configureRouting(applicationState = applicationState)

    val httpClients = HttpClients(environmentVariables)
    val kafkaClients = KafkaClients(environmentVariables)

    val storage: Storage = StorageOptions.newBuilder().build().service
    val bucketUploadService =
        BucketUploadService(
            environmentVariables.sykmeldingVedleggBucketName,
            storage,
        )
    val virusScanService = VirusScanService(httpClients.clamAvClient)

    val duplicationService = DuplicationService(database)

    launchListeners(
        environmentVariables,
        applicationState,
        httpClients.emottakSubscriptionClient,
        kafkaClients.kafkaProducerReceivedSykmelding,
        kafkaClients.kafkaProducerValidationResult,
        httpClients.syfoSykemeldingRuleClient,
        httpClients.pdlPersonService,
        applicationServiceUser,
        kafkaClients.manualValidationKafkaProducer,
        kafkaClients.kafkaProducerApprec,
        kafkaClients.kafkaproducerManuellOppgave,
        httpClients.norskHelsenettClient,
        bucketUploadService,
        virusScanService,
        duplicationService,
        httpClients.smtssClient,
        uploadSykmeldingService =
            UploadSykmeldingService(
                tsmSykmeldingBucket = environmentVariables.tsmSykmeldingBucket,
                storage = storage
            )
    )
}

@DelicateCoroutinesApi
fun createListener(
    applicationState: ApplicationState,
    action: suspend CoroutineScope.() -> Unit
): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            logger.error("En uhåndtert feil oppstod, applikasjonen restarter", e.cause)
        } finally {
            applicationState.ready = false
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    environmentVariables: EnvironmentVariables,
    applicationState: ApplicationState,
    emottakSubscriptionClient: EmottakSubscriptionClient,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmeldingWithValidation>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    pdlPersonService: PdlPersonService,
    serviceUser: ApplicationServiceUser,
    kafkaManuelTaskProducer: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    norskHelsenettClient: NorskHelsenettClient,
    bucketUploadService: BucketUploadService,
    virusScanService: VirusScanService,
    duplicationService: DuplicationService,
    smtssClient: SmtssClient,
    uploadSykmeldingService: UploadSykmeldingService,
) {
    createListener(applicationState) {
        connectionFactory(environmentVariables)
            .createConnection(serviceUser.serviceuserUsername, serviceUser.serviceuserPassword)
            .use { connection ->
                connection.start()
                val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

                val inputconsumer = session.consumerForQueue(environmentVariables.inputQueueName)
                val backoutProducer =
                    session.producerForQueue(environmentVariables.inputBackoutQueueName)

                BlockingApplicationRunner(
                        environmentVariables,
                        applicationState,
                        emottakSubscriptionClient,
                        syfoSykemeldingRuleClient,
                        norskHelsenettClient,
                        pdlPersonService,
                        bucketUploadService,
                        kafkaproducerreceivedSykmelding,
                        kafkaproducervalidationResult,
                        kafkaManuelTaskProducer,
                        kafkaproducerApprec,
                        kafkaproducerManuellOppgave,
                        virusScanService,
                        duplicationService,
                        smtssClient,
                        inputconsumer,
                        backoutProducer,
                        uploadSykmeldingService,
                        getUnleash()
                    )
                    .run()
            }
    }
}

fun sendReceipt(
    apprec: Apprec,
    apprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    loggingMeta: LoggingMeta,
) {
    try {
        kafkaproducerApprec.send(ProducerRecord(apprecTopic, apprec)).get()
        logger.info(
            "Apprec receipt sent to kafka topic $apprecTopic, {}",
            StructuredArguments.fields(loggingMeta),
        )
    } catch (ex: Exception) {
        logger.error("failed to send apprec to kafka {}", StructuredArguments.fields(loggingMeta))
        throw ex
    }
}

fun sendValidationResult(
    validationResult: ValidationResult,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    behandlingsUtfallTopic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    tsmProcessingTarget: Boolean,
) {
    try {

        val producerRecord =
            ProducerRecord(
                behandlingsUtfallTopic,
                receivedSykmelding.sykmelding.id,
                validationResult,
            )
        if (tsmProcessingTarget) {
            producerRecord.headers().add(PROCESSING_TARGET_HEADER, TSM_PROCESSING_TARGET_VALUE.toByteArray())
        }
        kafkaproducervalidationResult
            .send(
                producerRecord,
            )
            .get()
        logger.info(
            "Validation results send to kafka {}, {}",
            behandlingsUtfallTopic,
            StructuredArguments.fields(loggingMeta),
        )
    } catch (ex: Exception) {
        logger.error(
            "failed to send validation result for sykmelding {}",
            receivedSykmelding.sykmelding.id,
        )
        throw ex
    }
}

fun sendReceivedSykmelding(
    receivedSykmeldingTopic: String,
    receivedSykmelding: ReceivedSykmeldingWithValidation,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmeldingWithValidation>,
    tsmProcessingTarget: Boolean
) {

    try {
        logger.info(
            "Before sending sykmelding to kafka topic {} sykmelding id {}",
            receivedSykmeldingTopic,
            receivedSykmelding.sykmelding.id,
        )
        val record =
            ProducerRecord(
                receivedSykmeldingTopic,
                receivedSykmelding.sykmelding.id,
                receivedSykmelding,
            )
        if (tsmProcessingTarget) {
            record.headers().add(PROCESSING_TARGET_HEADER, TSM_PROCESSING_TARGET_VALUE.toByteArray())
        }
        kafkaproducerreceivedSykmelding.send(record).get()
        logger.info(
            "Sykmelding sendt to kafka topic {} sykmelding id {}",
            receivedSykmeldingTopic,
            receivedSykmelding.sykmelding.id,
        )
    } catch (ex: Exception) {
        logger.error(
            "failed to send sykmelding to kafka result for sykmelding {}",
            receivedSykmelding.sykmelding.id,
        )
        throw ex
    }
}

data class ApplicationState(
    var alive: Boolean = false,
    var ready: Boolean = false,
)

class ServiceUnavailableException(message: String?) : Exception(message)
