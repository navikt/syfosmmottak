package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import javax.jms.Session
import kotlinx.coroutines.CoroutineScope
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
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.ArbeidsFordelingClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.getFileAsString
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis

val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmmottak")

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val credentials = VaultCredentials(
            serviceuserPassword = getFileAsString("/secrets/serviceuser/password"),
            serviceuserUsername = getFileAsString("/secrets/serviceuser/username"),
            mqUsername = getFileAsString("/secrets/default/mqUsername"),
            mqPassword = getFileAsString("/secrets/default/mqPassword"),
            clientId = getFileAsString("/secrets/azuread/syfosmmottak/client_id"),
            clientsecret = getFileAsString("/secrets/azuread/syfosmmottak/client_secret"),
            redisSecret = getFileAsString("/secrets/default/redisSecret"),
            syfohelsenettproxyId = getFileAsString("/secrets/default/syfohelsenettproxyId")
    )
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
            env,
            applicationState)

    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()

    DefaultExports.initialize()

    val httpClients = HttpClients(env, credentials)
    val kafkaClients = KafkaClients(env, credentials)

    val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
        proxy { features.add(WSAddressingFeature()) }
        port { withBasicAuth(credentials.serviceuserUsername, credentials.serviceuserPassword) }
    }

    val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    val egenansattV1 = createPort<EgenAnsattV1>(env.egenAnsattURL) {
        port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
    }

    launchListeners(env, applicationState,
            subscriptionEmottak, kafkaClients.kafkaProducerReceivedSykmelding,
            kafkaClients.kafkaProducerValidationResult,
            httpClients.syfoSykemeldingRuleClient, httpClients.sarClient, httpClients.aktoerIdClient,
            httpClients.arbeidsFordelingClient, credentials, kafkaClients.manualValidationKafkaProducer,
            kafkaClients.kafkaProducerApprec, kafkaClients.kafkaproducerManuellOppgave,
            personV3, egenansattV1, httpClients.norskHelsenettClient)
}

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

@KtorExperimentalAPI
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    subscriptionEmottak: SubscriptionPort,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    kuhrSarClient: SarClient,
    aktoerIdClient: AktoerIdClient,
    arbeidsFordelingClient: ArbeidsFordelingClient,
    credentials: VaultCredentials,
    kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    personV3: PersonV3,
    egenAnsattV1: EgenAnsattV1,
    norskHelsenettClient: NorskHelsenettClient
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
            Jedis(env.redishost, 6379).use { jedis ->
                connection.start()
                val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

                val inputconsumer = session.consumerForQueue(env.inputQueueName)
                val syfoserviceProducer = session.producerForQueue(env.syfoserviceQueueName)
                val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)

                applicationState.ready = true

                jedis.auth(credentials.redisSecret)

                BlockingApplicationRunner().run(inputconsumer, syfoserviceProducer, backoutProducer,
                        subscriptionEmottak, kafkaproducerreceivedSykmelding, kafkaproducervalidationResult,
                        syfoSykemeldingRuleClient, kuhrSarClient, aktoerIdClient, arbeidsFordelingClient, env,
                        credentials, applicationState, jedis, kafkaManuelTaskProducer,
                        session, kafkaproducerApprec, kafkaproducerManuellOppgave,
                        personV3, egenAnsattV1, norskHelsenettClient)
            }
        }
    }
}

fun sendReceipt(
    apprec: Apprec,
    sm2013ApprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    try {
        kafkaproducerApprec.send(ProducerRecord(sm2013ApprecTopic, apprec)).get()
        log.info("Apprec receipt sent to kafka topic {}", sm2013ApprecTopic)
    } catch (ex: Exception) {
        log.error("failed to send apprec to kafka")
        throw ex
    }
}

fun sendValidationResult(
    validationResult: ValidationResult,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013BehandlingsUtfallToipic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta
) {

    try {
        kafkaproducervalidationResult.send(
                ProducerRecord(sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult)
        ).get()
        log.info("Validation results send to kafka {}, {}", sm2013BehandlingsUtfallToipic, fields(loggingMeta))
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
