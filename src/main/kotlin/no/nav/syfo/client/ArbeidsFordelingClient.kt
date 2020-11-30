package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import no.nav.syfo.log

@KtorExperimentalAPI
class ArbeidsFordelingClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun finnBehandlendeEnhet(
        arbeidsfordelingRequest: ArbeidsfordelingRequest
    ): List<ArbeidsfordelingResponse>? {
        val httpResponse = httpClient.post<HttpStatement>("$endpointUrl/enheter/bestmatch") {
            contentType(ContentType.Application.Json)
            val oidcToken = stsClient.oidcToken()
            header("Authorization", "Bearer ${oidcToken.access_token}")
            body = arbeidsfordelingRequest
        }.execute()

        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error("finnBehandlendeEnhet svarte med InternalServerError")
                return null
            }
            HttpStatusCode.BadRequest -> {
                log.error("finnBehandlendeEnhet svarer med BadRequest")
                return null
            }
            HttpStatusCode.NotFound -> {
                log.warn("finnBehandlendeEnhet svarer med NotFound")
                return null
            }
            HttpStatusCode.Unauthorized -> {
                log.warn("finnBehandlendeEnhet svarer med Unauthorized")
                return null
            }
            HttpStatusCode.OK -> {
                log.debug("finnBehandlendeEnhet svarer med httpResponse status kode: {}", httpResponse.status.value)
                return httpResponse.call.response.receive<List<ArbeidsfordelingResponse>>()
            }
        }
        return null
    }
}

data class ArbeidsfordelingRequest(
    val tema: String,
    val geografiskOmraade: String?,
    val behandlingstema: String?,
    val behandlingstype: String?,
    val oppgavetype: String,
    val diskresjonskode: String?,
    val skjermet: Boolean
)

data class ArbeidsfordelingResponse(
    val enhetId: String,
    val navn: String,
    val enhetNr: String?,
    val antallRessurser: String?,
    val status: String?,
    val orgNivaa: String?,
    val type: String?,
    val organisasjonsnummer: String?,
    val underEtableringDato: String?,
    val aktiveringsdato: String?,
    val underAvviklingDato: String?,
    val nedleggelsesdato: String?,
    val oppgavebehandler: String?,
    val versjon: String?,
    val sosialeTjenester: String?,
    val kanalstrategi: String?,
    val orgNrTilKommunaltNavKontor: String?
)
