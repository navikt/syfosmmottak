package no.nav.syfo

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import no.nav.syfo.nais.isalive.naisIsAliveRoute
import no.nav.syfo.nais.isready.naisIsReadyRoute
import no.nav.syfo.plugins.configureLifecycleHooks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class SelfTest {

    @Test
    internal fun `App is ready only after ServerReady is raised`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    naisIsAliveRoute(applicationState)
                    naisIsReadyRoute(applicationState)
                    configureLifecycleHooks(applicationState)
                }
                monitor.raise(ApplicationStarted, this)
                monitor.raise(ServerReady, this.environment)
            }

            val readyResponse = client.get("/internal/is_ready")
            assertEquals(HttpStatusCode.OK, readyResponse.status)
            assertEquals("I'm ready! :)", readyResponse.bodyAsText())
        }
    }

    @Test
    internal fun `Returns ok on is_alive`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = true
                    applicationState.alive = true
                    naisIsAliveRoute(applicationState)
                }
            }
            val response = client.get("/internal/is_alive")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("I'm alive! :)", response.bodyAsText())
        }
    }

    @Test
    internal fun `Returns ok in is_ready`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = true
                    applicationState.alive = true
                    naisIsReadyRoute(applicationState)
                }
            }
            val response = client.get("/internal/is_ready")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("I'm ready! :)", response.bodyAsText())
        }
    }

    @Test
    internal fun `Returns internal server error when liveness check fails`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = false
                    applicationState.alive = false
                    naisIsAliveRoute(applicationState)
                }
            }
            val response = client.get("/internal/is_alive")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("I'm dead x_x", response.bodyAsText())
        }
    }

    @Test
    internal fun `Returns internal server error when readyness check fails`() {
        testApplication {
            application {
                routing {
                    val applicationState = ApplicationState()
                    applicationState.ready = false
                    applicationState.alive = false
                    naisIsReadyRoute(applicationState)
                }
            }
            val response = client.get("/internal/is_ready")

            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertEquals("Please wait! I'm not ready :(", response.bodyAsText())
        }
    }
}
