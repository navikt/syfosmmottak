package no.nav.syfo.rerun

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.request.*
import io.ktor.server.routing.*
import jakarta.jms.Session
import no.nav.syfo.ApplicationServiceUser
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.application.sikkerlogg
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.duplicationcheck.db.deleteDuplicateCheckByMsgId
import no.nav.syfo.logger
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue

data class RerunRequest(
    val message: String,
    val duplicationMottakId: String?,
)

fun Route.registerRerunApi(
    serviceUser: ApplicationServiceUser,
    environmentVariables: EnvironmentVariables,
    database: DatabaseInterface
) {
    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
        jackson {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }
    }
    post("/internal/rerun") {
        try {
            logger.info("trying to rerun message")
            connectionFactory(environmentVariables)
                .createConnection(serviceUser.serviceuserUsername, serviceUser.serviceuserPassword)
                .use { connection ->
                    connection.start()
                    val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)

                    val backoutProducer =
                        session.producerForQueue(environmentVariables.inputBackoutQueueName)

                    val rerunMessage = call.receive<RerunRequest>()

                    if (rerunMessage.duplicationMottakId != null) {
                        val deleted =
                            database.deleteDuplicateCheckByMsgId(rerunMessage.duplicationMottakId)
                        logger.info("Deleted $deleted from duplication control")
                    }

                    val textMessage = session.createTextMessage(rerunMessage.message)

                    backoutProducer.send(textMessage)
                }
        } catch (ex: Exception) {
            logger.error("something bad happend in rerun, see securelogs for more details")
            sikkerlogg.error("Error while running rerun message", ex)
        }
    }
}
