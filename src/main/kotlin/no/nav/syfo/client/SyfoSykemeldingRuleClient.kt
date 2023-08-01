package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.logger
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta

class SyfoSykemeldingRuleClient(
    private val endpointUrl: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val client: HttpClient,
) {
    suspend fun executeRuleValidation(
        payload: ReceivedSykmelding,
        loggingMeta: LoggingMeta
    ): ValidationResult {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        val httpResponse =
            client.post("$endpointUrl/v1/rules/validate") {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                setBody(payload)
            }
        if (httpResponse.status == HttpStatusCode.OK) {
            return httpResponse.body<ValidationResult>()
        } else {
            logger.error(
                "Syfosmregler svarte med feilkode {} for {}",
                httpResponse.status,
                fields(loggingMeta)
            )
            throw IOException("Syfosmregler svarte med feilkode ${httpResponse.status}")
        }
    }
}
