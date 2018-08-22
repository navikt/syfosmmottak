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
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.syfo.api.registerNaisApi
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
import javax.xml.bind.JAXBContext
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
        session.close()

        val producerProperties = readProducerConfig(env, valueSerializer = StringSerializer::class)
        val kafkaproducer = KafkaProducer<String, String>(producerProperties)

        val syfoSykemeldingeeglerClient = SyfoSykemelginReglerClient(env.syfoSykemeldingRegelerApiURL,
                env.srvSyfoMottakUsername,
                env.srvSyfoMottakPassword)

        listen(inputQueue, connection, kafkaproducer, syfoSykemeldingeeglerClient).join()
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
    connection: Connection,
    kafkaproducer: KafkaProducer<String, String>,
    syfoSykemeldingeeglerClient: SyfoSykemelginReglerClient
) = launch {
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val consumer = session.createConsumer(inputQueue)

    consumer.setMessageListener {
        var defaultKeyValues = arrayOf(keyValue("noMessageIdentifier", true))
        var defaultKeyFormat = defaultLogInfo(defaultKeyValues)
        try {
            val inputMessageText = when (it) {
                is TextMessage -> it.text
                else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
            }
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            val ediLoggId = fellesformat.get<XMLMottakenhetBlokk>().ediLoggId

            // TODO: Do we want to use markers for this instead?
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

            val syfoSykemeldingeeglerClientReponse = syfoSykemeldingeeglerClient.executeRuleValidation("sting")
            // TODO syfoSykemeldingeeglerClientReponse valite if its manuelle or not
            if (true) {
                kafkaproducer.send(ProducerRecord("aapen-sykemelding-2013-automatisk-topic", "test value"))
            } else {
                kafkaproducer.send(ProducerRecord("aapen-sykemelding-2013-manuell-topic", "test value"))
            }
        } catch (e: Exception) {
            log.error("Exception caught while handling message, sending to backout $defaultKeyFormat",
                    *defaultKeyValues, e)
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
