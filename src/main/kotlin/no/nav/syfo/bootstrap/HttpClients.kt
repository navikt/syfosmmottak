package no.nav.syfo.bootstrap

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.engine.apache5.Apache5EngineConfig
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson3.jackson
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.logger
import no.nav.syfo.pdl.PdlFactory

class HttpClients(environmentVariables: EnvironmentVariables) {
    private val config: HttpClientConfig<Apache5EngineConfig>.() -> Unit = {
        install(ContentNegotiation) { jackson {} }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException ->
                        throw ServiceUnavailableException(exception.message)
                }
            }
        }
        install(HttpRequestRetry) {
            constantDelay(100, 0, false)
            retryOnExceptionIf(3) { request, throwable ->
                logger.warn("Caught exception ${throwable.message}, for url ${request.url}")
                true
            }
            retryIf(maxRetries) { request, response ->
                if (response.status.value.let { it in 500..599 }) {
                    logger.warn(
                        "Retrying for statuscode ${response.status.value}, for url ${request.url}"
                    )
                    true
                } else {
                    false
                }
            }
        }
        expectSuccess = false
    }

    private val httpClient = HttpClient(Apache5, config)

    private val accessTokenClientV2 =
        AccessTokenClientV2(
            environmentVariables.aadAccessTokenV2Url,
            environmentVariables.clientIdV2,
            environmentVariables.clientSecretV2,
            httpClient,
        )

    val syfoSykemeldingRuleClient =
        SyfoSykemeldingRuleClient(
            environmentVariables.syfosmreglerApiUrl,
            accessTokenClientV2,
            environmentVariables.syfosmreglerApiScope,
            httpClient,
        )

    val emottakSubscriptionClient =
        EmottakSubscriptionClient(
            environmentVariables.smgcpProxyUrl,
            accessTokenClientV2,
            environmentVariables.smgcpProxyScope,
            httpClient,
        )

    val norskHelsenettClient =
        NorskHelsenettClient(
            environmentVariables.norskHelsenettEndpointURL,
            accessTokenClientV2,
            environmentVariables.helsenettproxyScope,
            httpClient,
        )

    val pdlPersonService =
        PdlFactory.getPdlService(
            environmentVariables,
            httpClient,
            accessTokenClientV2,
            environmentVariables.pdlScope,
        )

    val clamAvClient = ClamAvClient(httpClient, environmentVariables.clamAvEndpointUrl)

    val smtssClient =
        SmtssClient(
            environmentVariables.smtssApiUrl,
            accessTokenClientV2,
            environmentVariables.smtssApiScope,
            httpClient,
        )
}
