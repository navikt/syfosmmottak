package no.nav.syfo.service

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.post
import jakarta.jms.Session
import no.nav.syfo.ApplicationServiceUser
import no.nav.syfo.ApplicationState
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.application.sikkerlogg
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.producerForQueue
import org.slf4j.LoggerFactory

fun Routing.rerunSykmeldingApi(env: EnvironmentVariables, applicationState: ApplicationState) {
    val log = LoggerFactory.getLogger("no.nav.syfo.rerun-api")
    val serviceUser = ApplicationServiceUser()
    val connection =
        connectionFactory(env)
            .createConnection(serviceUser.serviceuserUsername, serviceUser.serviceuserPassword)

    post("/internal/rerun") {
        val message = call.receiveText()
        log.info("Received message trying to rerun sykmelding")
        sikkerlogg.info("Received message trying to rerun sykmelding: $message")

        if (!applicationState.ready) {
            call.respondText("not ready", status = HttpStatusCode.TooEarly)
            return@post
        }
        connection.use { connection ->
            connection.start()
            val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
            val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)

            backoutProducer.send(session.createTextMessage(message))
        }

        call.respondText("OK")
    }
}
