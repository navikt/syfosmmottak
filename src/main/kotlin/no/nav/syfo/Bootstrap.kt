package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.BlockingApplicationRunner
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.bootstrap.HttpClients
import no.nav.syfo.bootstrap.KafkaClients
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.kafka.vedlegg.producer.KafkaVedleggProducer
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.ws.createPort
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import javax.jms.Session

val objectMapper: ObjectMapper = ObjectMapper()
    .registerModule(JavaTimeModule())
    .registerKotlinModule()
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmmottak")

fun main() {
    val env = Environment()
    val credentials = VaultCredentials(
        serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
        serviceuserUsername = getFileAsString("/secrets/serviceuser/username"),
        redisSecret = getEnvVar("REDIS_PASSWORD")
    )
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    DefaultExports.initialize()

    val httpClients = HttpClients(env, credentials)
    val kafkaClients = KafkaClients(env, credentials)

    val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
        proxy { features.add(WSAddressingFeature()) }
        port { withBasicAuth(credentials.serviceuserUsername, credentials.serviceuserPassword) }
    }

    launchListeners(
        env, applicationState,
        subscriptionEmottak, kafkaClients.kafkaProducerReceivedSykmelding,
        kafkaClients.kafkaProducerValidationResult,
        httpClients.syfoSykemeldingRuleClient, httpClients.sarClient, httpClients.pdlPersonService,
        credentials, kafkaClients.manualValidationKafkaProducer,
        kafkaClients.kafkaProducerApprec, kafkaClients.kafkaproducerManuellOppgave,
        httpClients.norskHelsenettClient, kafkaClients.kafkaVedleggProducer
    )
}

@DelicateCoroutinesApi
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (e: TrackableException) {
            log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", e.cause)
        } finally {
            applicationState.alive = false
        }
    }

@DelicateCoroutinesApi
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    subscriptionEmottak: SubscriptionPort,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    kuhrSarClient: SarClient,
    pdlPersonService: PdlPersonService,
    credentials: VaultCredentials,
    kafkaManuelTaskProducer: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    norskHelsenettClient: NorskHelsenettClient,
    kafkaVedleggProducer: KafkaVedleggProducer
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(credentials.serviceuserUsername, credentials.serviceuserPassword).use { connection ->
            Jedis(env.redisHost, 6379).use { jedis ->
                connection.start()
                val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

                val inputconsumer = session.consumerForQueue(env.inputQueueName)
                val syfoserviceProducer = session.producerForQueue(env.syfoserviceQueueName)
                val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)

                applicationState.ready = true

                jedis.auth(credentials.redisSecret)

                BlockingApplicationRunner(
                    env,
                    applicationState,
                    subscriptionEmottak,
                    syfoSykemeldingRuleClient,
                    norskHelsenettClient,
                    kuhrSarClient,
                    pdlPersonService,
                    jedis,
                    session,
                ).run(
                    inputconsumer,
                    syfoserviceProducer,
                    backoutProducer,
                    kafkaproducerreceivedSykmelding,
                    kafkaproducervalidationResult,
                    kafkaManuelTaskProducer,
                    kafkaproducerApprec,
                    kafkaproducerManuellOppgave,
                    kafkaVedleggProducer
                )
            }
        }
    }
}

fun sendReceipt(
    apprec: Apprec,
    apprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    try {
        kafkaproducerApprec.send(ProducerRecord(apprecTopic, apprec)).get()
        log.info("Apprec receipt sent to kafka topic {}", apprecTopic)
    } catch (ex: Exception) {
        log.error("failed to send apprec to kafka")
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
