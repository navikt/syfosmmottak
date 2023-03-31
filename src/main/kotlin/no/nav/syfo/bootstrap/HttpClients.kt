package no.nav.syfo.bootstrap

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import no.nav.syfo.Environment
import no.nav.syfo.application.exception.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.log
import no.nav.syfo.pdl.PdlFactory

class HttpClients(environment: Environment) {
    private val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                log.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    log.warn("Retrying for statuscode ${response.status.value}, for url ${request.url}")
                    true
                } else {
                    false
                }
            }
        }
        expectSuccess = false
    }

    private val httpClient = HttpClient(Apache, config)

    private val accessTokenClientV2 = AccessTokenClientV2(
        environment.aadAccessTokenV2Url,
        environment.clientIdV2,
        environment.clientSecretV2,
        httpClient,
    )

    val syfoSykemeldingRuleClient = SyfoSykemeldingRuleClient(
        environment.syfosmreglerApiUrl,
        accessTokenClientV2,
        environment.syfosmreglerApiScope,
        httpClient,
    )

    val sarClient = SarClient(environment.smgcpProxyUrl, accessTokenClientV2, environment.smgcpProxyScope, httpClient)

    val emottakSubscriptionClient = EmottakSubscriptionClient(environment.smgcpProxyUrl, accessTokenClientV2, environment.smgcpProxyScope, httpClient)

    val norskHelsenettClient = NorskHelsenettClient(environment.norskHelsenettEndpointURL, accessTokenClientV2, environment.helsenettproxyScope, httpClient)

    val pdlPersonService = PdlFactory.getPdlService(environment, httpClient, accessTokenClientV2, environment.pdlScope)

    val clamAvClient = ClamAvClient(httpClient, environment.clamAvEndpointUrl)

    val smtssClient = SmtssClient(environment.smtssApiUrl, accessTokenClientV2, environment.smtssApiScope, httpClient)
}
