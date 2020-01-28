package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.receive
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpStatement
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.util.KtorExperimentalAPI
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.log
import no.nav.syfo.model.IdentInfoResult
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class AktoerIdClient(
    private val endpointUrl: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {
    suspend fun getAktoerIds(
        personNumbers: List<String>,
        username: String,
        loggingMeta: LoggingMeta
    ): Map<String, IdentInfoResult> {
        val httpResponse = httpClient.get<HttpStatement>("$endpointUrl/identer") {
            accept(ContentType.Application.Json)
            val oidcToken = stsClient.oidcToken()
            headers {
                append("Authorization", "Bearer ${oidcToken.access_token}")
                append("Nav-Consumer-Id", username)
                append("Nav-Call-Id", loggingMeta.msgId)
                append("Nav-Personidenter", personNumbers.joinToString(","))
            }
            parameter("gjeldende", "true")
            parameter("identgruppe", "AktoerId")
        }.execute()

        when (httpResponse.status) {
            HttpStatusCode.InternalServerError -> {
                log.error("AktorRegisteret svarte med feilmelding for {}", fields(loggingMeta))
                throw IOException("AktorRegisteret svarte med feilmelding for msgid ${loggingMeta.msgId}")
            }
            else -> {
                log.info("AktorRegisteret gav ein Http responsen kode er {}, for {}", httpResponse.status, fields(loggingMeta))
                log.info("Henter pasient og avsenderSamhandler for {}", fields(loggingMeta))
                return httpResponse.call.response.receive<Map<String, IdentInfoResult>>()
            }
        }
    }
}
