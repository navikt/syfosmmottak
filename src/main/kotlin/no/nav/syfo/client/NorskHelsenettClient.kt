package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import java.io.IOException

class NorskHelsenettClient(
    private val endpointUrl: String,
    private val accessTokenClient: AccessTokenClient,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    @KtorExperimentalAPI
    suspend fun finnBehandler(hprNummer: String, msgId: String): Behandler? {
        return retry(
            callName = "finnbehandler",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L)
        ) {
            try {
                log.info("Henter behandler fra syfohelsenettproxy for msgId {}", msgId)
                return@retry httpClient.get<Behandler>("$endpointUrl/api/behandlerMedHprNummer") {
                    accept(ContentType.Application.Json)
                    val accessToken = accessTokenClient.hentAccessToken(resourceId)
                    headers {
                        append("Authorization", "Bearer $accessToken")
                        append("Nav-CallId", msgId)
                        append("hprNummer", hprNummer)
                    }
                }.also {
                    log.info("Hentet behandler for msgId {}", msgId)
                }
            } catch (e: ResponseException) {
                if (e.response.status == NotFound) {
                    log.warn("Fant ikke behandler for HprNummer $hprNummer for msgId $msgId")
                    null
                } else {
                    log.error("Syfohelsenettproxy kastet feilmelding {} for msgId {} ved søk på hprNummer {}", e.message, msgId, hprNummer)
                    throw IOException("Syfohelsenettproxy kastet feilmelding ${e.message}")
                }
            }
        }
    }
}

data class Behandler(
    val godkjenninger: List<Godkjenning>,
    val fnr: String?,
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
