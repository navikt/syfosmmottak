package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.helpers.retry
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import java.io.IOException

@KtorExperimentalAPI
class SyfoSykemeldingRuleClient(private val endpointUrl: String, private val client: HttpClient) {
    suspend fun executeRuleValidation(payload: ReceivedSykmelding, loggingMeta: LoggingMeta): ValidationResult =
        retry("syfosmregler_validate") {
            try {
                client.post<ValidationResult>("$endpointUrl/v1/rules/validate") {
                    contentType(ContentType.Application.Json)
                    accept(ContentType.Application.Json)
                    body = payload
                }
            } catch (e: ResponseException) {
                log.error("Syfosmregler kastet feilmelding {} for {}", e.message, fields(loggingMeta))
                throw IOException("Syfosmregler kastet feilmelding ${e.message}")
            }
        }
}
