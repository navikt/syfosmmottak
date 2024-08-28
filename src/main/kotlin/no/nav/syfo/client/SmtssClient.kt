package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.application.sikkerlogg
import no.nav.syfo.logger
import no.nav.syfo.util.LoggingMeta

class SmtssClient(
    private val endpointUrl: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient,
) {
    suspend fun findBestTssIdEmottak(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): String? {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)

        sikkerlogg.info(
            "Henter TSS-id fra smtss sykmeldingId: $sykmeldingId, samhandlerFnr: $samhandlerFnr, samhandlerOrgName: $samhandlerOrgName loggingMeta: {}",
            StructuredArguments.fields(loggingMeta)
        )
        val httpResponse =
            httpClient.get("$endpointUrl/api/v1/samhandler/emottak") {
                accept(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                header("requestId", sykmeldingId)
                header("samhandlerFnr", samhandlerFnr)
                header("samhandlerOrgName", samhandlerOrgName)
            }
        return getResponse(httpResponse, loggingMeta)
    }

    suspend fun findBestTssInfotrygdId(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
        sykmeldingId: String,
    ): String? {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        val httpResponse =
            httpClient.get("$endpointUrl/api/v1/samhandler/infotrygd") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                header("requestId", sykmeldingId)
                header("samhandlerFnr", samhandlerFnr)
                header("samhandlerOrgName", samhandlerOrgName)
            }
        return getResponse(httpResponse, loggingMeta)
    }

    private suspend fun getResponse(
        httpResponse: io.ktor.client.statement.HttpResponse,
        loggingMeta: LoggingMeta
    ): String? {
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.body<TSSident>().tssid
            }
            HttpStatusCode.NotFound -> {
                logger.info(
                    "smtss responded with {} for {}",
                    httpResponse.status,
                    StructuredArguments.fields(loggingMeta),
                )
                null
            }
            else -> {
                logger.error(
                    "Error getting TSS-id ${httpResponse.status} : ${httpResponse.bodyAsText()}"
                )
                throw RuntimeException("Error getting TSS-id")
            }
        }
    }
}

data class TSSident(
    val tssid: String,
)
