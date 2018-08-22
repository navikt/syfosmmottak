package no.nav.syfo

import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.runBlocking
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.model.fellesformat.EIFellesformat
import no.nav.model.syfomottak.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.api.registerNaisApi
import org.slf4j.LoggerFactory
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.jms.Connection
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller

fun doReadynessCheck(): Boolean {
    // Do validation
    return true
}

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(EIFellesformat::class.java, HelseOpplysningerArbeidsuforhet::class.java)
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
        listen(inputQueue, connection).join()
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
        connection: Connection
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
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as EIFellesformat
            val ediLoggId = fellesformat.mottakenhetBlokk.ediLoggId

        }
        catch (e: Exception) {
            log.error("Exception caught while handling message, sending to backout $defaultKeyFormat",
                    *defaultKeyValues, e)
        }
    }

}

fun defaultLogInfo(keyValues: Array<StructuredArgument>): String =
        (0..(keyValues.size - 1)).joinToString(", ", "(", ")") { "{}" }