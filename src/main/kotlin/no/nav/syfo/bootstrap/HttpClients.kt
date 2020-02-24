package no.nav.syfo.bootstrap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.ArbeidsFordelingClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient

class HttpClients(environment: Environment, credentials: VaultCredentials) {

    private val simpleHttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }

    private val httpClientMedBasicAuth = HttpClient(Apache) {
        install(Auth) {
            basic {
                username = credentials.serviceuserUsername
                password = credentials.serviceuserPassword
                sendWithoutRequest = true
            }
        }
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        expectSuccess = false
    }
    @KtorExperimentalAPI
    val syfoSykemeldingRuleClient = SyfoSykemeldingRuleClient(environment.syfosmreglerApiUrl, httpClientMedBasicAuth)
    @KtorExperimentalAPI
    val sarClient = SarClient(environment.kuhrSarApiUrl, simpleHttpClient)
    @KtorExperimentalAPI
    val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
    @KtorExperimentalAPI
    val aktoerIdClient = AktoerIdClient(environment.aktoerregisterV1Url, oidcClient, simpleHttpClient)
    @KtorExperimentalAPI
    val arbeidsFordelingClient = ArbeidsFordelingClient(environment.arbeidsfordelingAPIUrl, oidcClient, simpleHttpClient)
}
