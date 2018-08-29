package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.apprec._2004_11_21.XMLAppRec
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.model.sykemelding2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.api.Status
import no.nav.syfo.api.createHttpClient
import no.nav.syfo.api.executeRuleValidation
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.apprec.ApprecError
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.apprec.mapApprecErrorToAppRecCV
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.util.connectionFactory
import no.nav.syfo.util.readProducerConfig
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Connection
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage
import org.apache.kafka.clients.producer.ProducerRecord
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisSentinelPool
import redis.clients.jedis.exceptions.JedisConnectionException
import java.io.StringReader
import java.io.StringWriter
import java.security.MessageDigest
import javax.jms.MessageProducer
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller

fun doReadynessCheck(): Boolean {
    return true
}

val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLMsgHead::class.java, HelseOpplysningerArbeidsuforhet::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller()

val apprecJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLAppRec::class.java)
val apprecMarshaller: Marshaller = apprecJaxBContext.createMarshaller()

val redisMasterName = "mymaster"

data class ApplicationState(var running: Boolean = true)

private val log = LoggerFactory.getLogger("nav.syfomottak-application")

fun main(args: Array<String>) = runBlocking {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })

    connectionFactory(env).createConnection(env.srvappserverUsername, env.srvappserverPassword).use { connection ->
        connection.start()
        JedisSentinelPool(redisMasterName, setOf("${env.redisHost}:26379")).resource.use {
            jedis ->
            val session = connection.createSession()
            val inputQueue = session.createQueue(env.syfomottakinputQueueName)
            val receiptQueue = session.createQueue(env.apprecQueue)
            val backoutQueue = session.createQueue(env.syfomottakinputBackoutQueueName)
            session.close()

            val producerProperties = readProducerConfig(env, valueSerializer = StringSerializer::class)
            val kafkaproducer = KafkaProducer<String, String>(producerProperties)

            val httpClient = createHttpClient(env)

            listen(inputQueue, receiptQueue, backoutQueue, connection, kafkaproducer, httpClient, env, applicationState, jedis).join()
        }
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}

fun listen(
    inputQueue: Queue,
    receiptQueue: Queue,
    backoutQueue: Queue,
    connection: Connection,
    kafkaproducer: KafkaProducer<String, String>,
    httpClient: HttpClient,
    env: Environment,
    applicationState: ApplicationState,
    jedis: Jedis
) = launch {
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val consumer = session.createConsumer(inputQueue)
    val receiptProducer = session.createProducer(receiptQueue)
    val backoutProducer = session.createProducer(backoutQueue)

    while (applicationState.running) {
        val message = consumer.receiveNoWait()
        if (message == null) {
            delay(100)
            continue
        }
        var defaultKeyValues = arrayOf(keyValue("noMessageIdentifier", true))
        var defaultKeyFormat = defaultLogInfo(defaultKeyValues)
        try {
            val inputMessageText = when (message) {
                is TextMessage -> message.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
            }
            val requestLatency = REQUEST_TIME.startTimer()
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat

            val ediLoggId = fellesformat.get<XMLMottakenhetBlokk>().ediLoggId
            val sha256String = sha256hashstring(extractHelseOpplysningerArbeidsuforhet(fellesformat))

            defaultKeyValues = arrayOf(
                    keyValue("organisationNumber", extractOrganisationNumberFromSender(fellesformat)?.id),
                    keyValue("ediLoggId", fellesformat.get<XMLMottakenhetBlokk>().ediLoggId),
                    keyValue("msgId", fellesformat.get<XMLMsgHead>().msgInfo.msgId)
            )

            defaultKeyFormat = defaultLogInfo(defaultKeyValues)

            log.info("Received message from {}, $defaultKeyFormat",
                    keyValue("size", inputMessageText.length),
                    *defaultKeyValues)

            if (log.isDebugEnabled) {
                log.debug("Incoming message {}, $defaultKeyFormat",
                        keyValue("xmlMessage", inputMessageText),
                        *defaultKeyValues)
            }

            try {
                val redisEdiLoggId = jedis.get(sha256String)
                val duplicate = redisEdiLoggId != null

                if (duplicate) {
                    log.warn("Message marked as duplicate $defaultKeyFormat", redisEdiLoggId, *defaultKeyValues)
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, ApprecError.DUPLICATE)
                    log.info("Apprec Receipt sent to {} $defaultKeyFormat", env.apprecQueue, *defaultKeyValues)
                    continue
                } else if (ediLoggId != null) {
                    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
                }
            } catch (connectionException: JedisConnectionException) {
                log.warn("Unable to contact redis, will allow possible duplicates.", connectionException)
            }

            val validationResult = httpClient.executeRuleValidation(inputMessageText)
            when {
                validationResult.status == Status.OK -> {
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
                    log.info("Apprec Receipt sent to {} $defaultKeyFormat", env.apprecQueue, *defaultKeyValues)
                    kafkaproducer.send(ProducerRecord(env.kafkaSM2013JournalfoeringTopic, inputMessageText))
                    log.info("Message send to kafka {} $defaultKeyFormat", env.kafkaSM2013JournalfoeringTopic, *defaultKeyValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $defaultKeyFormat has outcome automatic, processing took {}s",
                            *defaultKeyValues, currentRequestLatency)
                }
                validationResult.status == Status.MANUAL_PROCESSING -> {
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
                    log.info("Apprec Receipt sent to {} $defaultKeyFormat", env.apprecQueue, *defaultKeyValues)
                    kafkaproducer.send(ProducerRecord(env.kafkaSM2013OppgaveGsakTopic, inputMessageText))
                    log.info("Message send to kafka {} $defaultKeyFormat", env.kafkaSM2013OppgaveGsakTopic, *defaultKeyValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $defaultKeyFormat has outcome manual processing, processing took {}s",
                            *defaultKeyValues, currentRequestLatency)
                }
                validationResult.status == Status.INVALID -> {
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist)
                    log.info("Apprec Receipt sent to {} $defaultKeyFormat", env.apprecQueue, *defaultKeyValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $defaultKeyFormat has outcome return, processing took {}s",
                            *defaultKeyValues, currentRequestLatency)
                }
            }
        } catch (e: Exception) {
            log.error("Exception caught while handling message, sending to backout $defaultKeyFormat",
                    *defaultKeyValues, e)
            backoutProducer.send(message)
        }
    }
}

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun defaultLogInfo(keyValues: Array<StructuredArgument>): String =
        (0..(keyValues.size - 1)).joinToString(", ", "(", ")") { "{}" }

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
        fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
            it.typeId.v == "ENH"
        }

fun sendReceipt(
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    apprecStatus: ApprecStatus,
    vararg apprecErrors: ApprecError
) {
    receiptProducer.send(session.createTextMessage().apply {
        val fellesformat = createApprec(fellesformat, apprecStatus)
        fellesformat.get<XMLAppRec>().error.addAll(apprecErrors.map { mapApprecErrorToAppRecCV(it) })
        text = apprecMarshaller.toString(fellesformat)
    })
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

fun sha256hashstring(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): String =
        MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(helseOpplysningerArbeidsuforhet))
                .fold("") { str, it -> str + "%02x".format(it) }

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
        fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet