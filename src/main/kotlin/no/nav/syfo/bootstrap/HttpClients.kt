package no.nav.syfo.bootstrap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.HttpResponseValidator
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.network.sockets.SocketTimeoutException
import no.nav.syfo.Environment
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.pdl.PdlFactory
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import java.net.ProxySelector

class HttpClients(environment: Environment) {

    private val simpleHttpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseException { exception ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
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

    private val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }

    private val httpClientWithProxy = HttpClient(Apache, proxyConfig)

    private val accessTokenClientV2 = AccessTokenClientV2(
        environment.aadAccessTokenV2Url,
        environment.clientIdV2,
        environment.clientSecretV2,
        httpClientWithProxy
    )

    val syfoSykemeldingRuleClient = SyfoSykemeldingRuleClient(
        environment.syfosmreglerApiUrl,
        accessTokenClientV2,
        environment.syfosmreglerApiScope,
        simpleHttpClient
    )

    val sarClient = SarClient(environment.kuhrSarApiUrl, accessTokenClientV2, environment.kuhrSarApiScope, simpleHttpClient)

    val norskHelsenettClient = NorskHelsenettClient(environment.norskHelsenettEndpointURL, accessTokenClientV2, environment.helsenettproxyScope, simpleHttpClient)

    val pdlPersonService = PdlFactory.getPdlService(environment, simpleHttpClient, accessTokenClientV2, environment.pdlScope)
}
