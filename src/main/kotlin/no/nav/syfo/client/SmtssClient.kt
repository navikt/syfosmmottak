package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import java.io.IOException

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
    ) {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        val httpResponse = httpClient.get("$endpointUrl/api/v1/samhandler/emottak") {
            accept(ContentType.Application.Json)
            parameter("samhandlerFnr", samhandlerFnr)
            parameter("samhandlerOrgName", samhandlerOrgName)
            header("Authorization", "Bearer $accessToken")
        }
        if (httpResponse.status == HttpStatusCode.OK) {
            val tssid = httpResponse.body<TSSident>().tssid
            log.info("Found tssid: $tssid for {}", StructuredArguments.fields(loggingMeta))
        } else {
            log.info(
                "smtss responded with an error code {} for {}",
                httpResponse.status,
                StructuredArguments.fields(loggingMeta),
            )
            throw IOException("smtss responded with an error code ${httpResponse.status}")
        }
    }

    suspend fun findBestTssInfotrygdId(
        samhandlerFnr: String,
        samhandlerOrgName: String,
        loggingMeta: LoggingMeta,
    ) {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        val httpResponse = httpClient.get("$endpointUrl/api/v1/samhandler/infotrygd") {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            parameter("samhandlerFnr", samhandlerFnr)
            parameter("samhandlerOrgName", samhandlerOrgName)
            header("Authorization", "Bearer $accessToken")
        }
        if (httpResponse.status == HttpStatusCode.OK) {
            val tssid = httpResponse.body<TSSident>().tssid
            log.info("Found tssid: $tssid for {}", StructuredArguments.fields(loggingMeta))
        } else {
            log.info(
                "smtss responded with an error code {} for {}",
                httpResponse.status,
                StructuredArguments.fields(loggingMeta),
            )
            throw IOException("smtss responded with an error code ${httpResponse.status}")
        }
    }
}

data class TSSident(
    val tssid: String,
)
