package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.apprec._2004_11_21.XMLAppRec
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.syfo.api.Status
import no.nav.syfo.api.SyfoSykemelginReglerClient
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.apprec.ApprecError
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.apprec.mapApprecErrorToAppRecCV
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.util.connectionFactory
import no.nav.syfo.util.readProducerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Connection
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage
import no.nav.xml.eiff._2.XMLEIFellesformat
import no.nav.xml.eiff._2.XMLMottakenhetBlokk
import org.apache.kafka.clients.producer.ProducerRecord
import java.io.StringReader
import java.io.StringWriter
import javax.jms.MessageProducer
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller

fun doReadynessCheck(): Boolean {
    // Do validation
    return true
}

val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller()

val apprecJaxBContext: JAXBContext = JAXBContext.newInstance(XMLAppRec::class.java)
val apprecMarshaller: Marshaller = apprecJaxBContext.createMarshaller()

data class ApplicationState(var running: Boolean = true)

private val log = LoggerFactory.getLogger("nav.syfomottak-application")

fun main(args: Array<String>) = runBlocking {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = true)

    connectionFactory(env).createConnection(env.srvappserverUsername, env.srvappserverPassword).use {
        connection ->
        connection.start()

        val session = connection.createSession()
        val inputQueue = session.createQueue(env.syfomottakinputQueueName)
        val receiptQueue = session.createQueue(env.apprecQueue)
        val backoutQueue = session.createQueue(env.syfomottakinputBackoutQueueName)
        session.close()

        val producerProperties = readProducerConfig(env, valueSerializer = StringSerializer::class)
        val kafkaproducer = KafkaProducer<String, String>(producerProperties)

        val syfoSykemeldingeeglerClient = SyfoSykemelginReglerClient(env.syfoSykemeldingRegelerApiURL,
                env.srvSyfoMottakUsername,
                env.srvSyfoMottakPassword)

        listen(inputQueue, receiptQueue, backoutQueue, connection, kafkaproducer, syfoSykemeldingeeglerClient, env).join()
    }

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })
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
    syfoSykemeldingeeglerClient: SyfoSykemelginReglerClient,
    env: Environment
) = launch {
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val consumer = session.createConsumer(inputQueue)
    val receiptProducer = session.createProducer(receiptQueue)
    val backoutProducer = session.createProducer(backoutQueue)

    consumer.setMessageListener {
        var defaultKeyValues = arrayOf(keyValue("noMessageIdentifier", true))
        var defaultKeyFormat = defaultLogInfo(defaultKeyValues)
        try {
            val inputMessageText = when (it) {
                is TextMessage -> it.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
            }
            val requestLatency = REQUEST_TIME.startTimer()
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat

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

            val validationResult = syfoSykemeldingeeglerClient.executeRuleValidation("sting")
            // TODO syfoSykemeldingeeglerClientReponse valite if its manuelle or not
            if (validationResult.status == Status.OK) {
                sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
                val currentRequestLatency = requestLatency.observeDuration()
                log.info("Message $defaultKeyFormat has been sent in return, processing took {}s",
                        *defaultKeyValues, currentRequestLatency)
                kafkaproducer.send(ProducerRecord(env.kafkaSM2013JournalfoeringTopic, inputMessageText))
            } else if (validationResult.status == Status.MANUAL_PROCESSING) {
                sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
                val currentRequestLatency = requestLatency.observeDuration()
                log.info("Message $defaultKeyFormat has been sent in return, processing took {}s",
                        *defaultKeyValues, currentRequestLatency)
                kafkaproducer.send(ProducerRecord(env.kafkaSM2013JournalfoeringTopic, inputMessageText))
            } else if (validationResult.status == Status.INVALID) {
                sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist)
                val currentRequestLatency = requestLatency.observeDuration()
                log.info("Message $defaultKeyFormat has been sent in return, processing took {}s",
                        *defaultKeyValues, currentRequestLatency)
            }
        } catch (e: Exception) {
            log.error("Exception caught while handling message, sending to backout $defaultKeyFormat",
                    *defaultKeyValues, e)
            backoutProducer.send(it)
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
