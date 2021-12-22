package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.NotFound
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import java.io.IOException
import java.lang.RuntimeException

class NorskHelsenettClient(
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    suspend fun getByHpr(hprNummer: String?, loggingMeta: LoggingMeta): Behandler? {
        return retry(
            callName = "finnbehandler",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L)
        ) {
            val accessToken = accessTokenClient.getAccessTokenV2(resourceId)

            val httpResponse = httpClient.get<HttpStatement>("$endpointUrl/api/v2/behandlerMedHprNummer") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("Nav-CallId", loggingMeta.msgId)
                    append("hprNummer", hprNummer!!)
                }
            }.execute()
            if (httpResponse.status == HttpStatusCode.InternalServerError) {
                log.error("Syfohelsenettproxy kastet feilmelding for loggingMeta {} ved henting av behandler for hprNummer", fields(loggingMeta))
                throw IOException("Syfohelsenettproxy kastet feilmelding og svarte status ${httpResponse.status} ved søk på hprNummer")
            }
            when (httpResponse.status) {
                NotFound -> {
                    log.warn("Fant ikke behandler for hprNummer {}", fields(loggingMeta))
                    null
                }
                HttpStatusCode.Unauthorized -> {
                    log.error("Norsk helsenett returnerte Unauthorized for henting av behandler for hprNummer")
                    throw RuntimeException("Norsk helsenett returnerte Unauthorized ved henting av behandler for hprNummer")
                }
                HttpStatusCode.OK -> {
                    log.info("Hentet behandler for hprNummer {}", fields(loggingMeta))
                    httpResponse.call.response.receive<Behandler>()
                }
                else -> {
                    log.error("Feil ved henting av behandler. Statuskode: ${httpResponse.status}")
                    throw RuntimeException("En ukjent feil oppsto ved ved henting av behandler ved søk på hprNummer. Statuskode: ${httpResponse.status}")
                }
            }
        }
    }

    suspend fun getByFnr(fnr: String, loggingMeta: LoggingMeta): Behandler? {
        return retry(
            callName = "finnbehandler",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L)
        ) {
            val accessToken = accessTokenClient.getAccessTokenV2(resourceId)

            val httpResponse = httpClient.get<HttpStatement>("$endpointUrl/api/v2/behandler") {
                accept(ContentType.Application.Json)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("Nav-CallId", loggingMeta.msgId)
                    append("behandlerFnr", fnr)
                }
            }.execute()
            if (httpResponse.status == HttpStatusCode.InternalServerError) {
                log.error("Syfohelsenettproxy kastet feilmelding for loggingMeta {} ved henting av behandler for fnr", fields(loggingMeta))
                throw IOException("Syfohelsenettproxy kastet feilmelding og svarte status ${httpResponse.status} ved søk på fnr")
            }
            when (httpResponse.status) {
                NotFound -> {
                    log.warn("Fant ikke behandler for fnr {}", fields(loggingMeta))
                    null
                }
                HttpStatusCode.Unauthorized -> {
                    log.error("Norsk helsenett returnerte Unauthorized for henting av behandler")
                    throw RuntimeException("Norsk helsenett returnerte Unauthorized ved henting av behandler")
                }
                HttpStatusCode.OK -> {
                    log.info("Hentet behandler for fnr {}", fields(loggingMeta))
                    httpResponse.call.response.receive<Behandler>()
                }
                else -> {
                    log.error("Feil ved henting av behandler. Statuskode: ${httpResponse.status}")
                    throw RuntimeException("En ukjent feil oppsto ved ved henting av behandler. Statuskode: ${httpResponse.status}")
                }
            }
        }
    }
}

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
    val hprNummer: String?,
    val fornavn: String?,
    val mellomnavn: String?,
    val etternavn: String?
)

data class Godkjenning(
    val helsepersonellkategori: Kode? = null,
    val autorisasjon: Kode? = null
)

data class Kode(
    val aktiv: Boolean,
    val oid: Int,
    val verdi: String?
)
