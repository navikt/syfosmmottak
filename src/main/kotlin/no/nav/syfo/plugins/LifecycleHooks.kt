package no.nav.syfo.plugins

import io.ktor.server.application.*
import no.nav.syfo.ApplicationState

fun Application.configureLifecycleHooks(applicationState: ApplicationState) {

    monitor.subscribe(ApplicationStarted) { applicationState.alive = true }
    monitor.subscribe(ServerReady) { applicationState.ready = true }
    monitor.subscribe(ApplicationStopped) {
        applicationState.ready = false
        applicationState.alive = false
    }
}
