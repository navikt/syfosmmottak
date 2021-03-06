package no.nav.syfo.bootstrap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.pdl.PdlFactory
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

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

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
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

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    @KtorExperimentalAPI
    val syfoSykemeldingRuleClient = SyfoSykemeldingRuleClient(environment.syfosmreglerApiUrl, httpClientMedBasicAuth)
    @KtorExperimentalAPI
    val sarClient = SarClient(environment.kuhrSarApiUrl, simpleHttpClient)
    @KtorExperimentalAPI
    val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword, environment.securityTokenServiceUrl)
    @KtorExperimentalAPI
    val accessTokenClient = AccessTokenClient(environment.aadAccessTokenUrl, credentials.clientId, credentials.clientsecret, httpClientWithProxy)
    @KtorExperimentalAPI
    val norskHelsenettClient = NorskHelsenettClient(environment.norskHelsenettEndpointURL, accessTokenClient, credentials.syfohelsenettproxyId, simpleHttpClient)
    @KtorExperimentalAPI
    val pdlPersonService = PdlFactory.getPdlService(environment, oidcClient, simpleHttpClient)
}
