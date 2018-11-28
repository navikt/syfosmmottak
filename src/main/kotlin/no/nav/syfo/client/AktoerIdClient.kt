package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import no.nav.syfo.model.IdentInfoResult

class AktoerIdClient(private val endpointUrl: String, private val stsClient: StsOidcClient) {
    private val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
    }

    suspend fun getAktoerIds(personNumbers: List<String>, trackingId: String): Map<String, IdentInfoResult> =
            client.get("$endpointUrl/identer") {
                accept(ContentType.Application.Json)
                val oidcToken = stsClient.oidcToken()
                headers {
                    append("Authorization", "Bearer ${oidcToken.access_token}")
                    append("Nav-Consumer-Id", "syfosmjoark")
                    append("Nav-Call-Id", trackingId)
                    appendAll("Nav-Personidenter", personNumbers)
                }
                parameter("gjeldende", "true")
                parameter("identgruppe", "AktoerId")
            }
}