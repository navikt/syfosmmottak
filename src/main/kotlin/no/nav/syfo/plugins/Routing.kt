package no.nav.syfo.plugins

import io.ktor.server.application.*
import io.ktor.server.routing.*
import no.nav.syfo.ApplicationState
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.nais.isalive.naisIsAliveRoute
import no.nav.syfo.nais.isready.naisIsReadyRoute
import no.nav.syfo.nais.prometheus.naisPrometheusRoute
import no.nav.syfo.service.rerunSykmeldingApi

fun Application.configureRouting(
    applicationState: ApplicationState,
    environment: EnvironmentVariables
) {
    routing {
        naisIsAliveRoute(applicationState)
        naisIsReadyRoute(applicationState)
        rerunSykmeldingApi(environment, applicationState)
        naisPrometheusRoute()
    }
}
