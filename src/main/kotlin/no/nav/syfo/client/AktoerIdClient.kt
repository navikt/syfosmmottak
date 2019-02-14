package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.Deferred
import no.nav.syfo.model.IdentInfoResult
import no.nav.syfo.retryAsync

@KtorExperimentalAPI
class AktoerIdClient(private val endpointUrl: String, private val stsClient: StsOidcClient) {
    private val client = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }
    }

    suspend fun getAktoerIds(personNumbers: List<String>, trackingId: String, username: String): Deferred<Map<String, IdentInfoResult>> = client.retryAsync("aktoerid_identer") {
        client.get<Map<String, IdentInfoResult>>("$endpointUrl/identer") {
            accept(ContentType.Application.Json)
            val oidcToken = stsClient.oidcToken()
            headers {
                append("Authorization", "Bearer ${oidcToken.access_token}")
                append("Nav-Consumer-Id", username)
                append("Nav-Call-Id", trackingId)
                append("Nav-Personidenter", personNumbers.joinToString(","))
            }
            parameter("gjeldende", "true")
            parameter("identgruppe", "AktoerId")
        }
    }
}
