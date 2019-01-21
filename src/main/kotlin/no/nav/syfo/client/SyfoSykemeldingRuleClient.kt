package no.nav.syfo.client

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.config
import io.ktor.client.features.auth.basic.BasicAuth
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.accept
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import no.nav.syfo.VaultCredentials
import no.nav.syfo.model.ReceivedSykmelding
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.syfo.http")

fun createHttpClient(credentials: VaultCredentials) = HttpClient(Apache.config {
    /*  maxConnectionsCount = 1000 // Maximum number of socket connections.
    endpoint.apply {
        maxConnectionsPerRoute = 100
        pipelineMaxSize = 20
        keepAliveTime = 5000
        connectTimeout = 5000
        connectRetryAttempts = 5
    } */
}) {
    install(BasicAuth) {
        username = credentials.serviceuserUsername
        password = credentials.serviceuserPassword
    }
    install(JsonFeature) {
        serializer = JacksonSerializer {
            registerKotlinModule()
            registerModule(JavaTimeModule())
            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        }
    }
}

suspend fun HttpClient.executeRuleValidation(payload: ReceivedSykmelding): ValidationResult = post {
    contentType(ContentType.Application.Json)
    body = payload
    accept(ContentType.Application.Json)

    url {
        host = "syfosmregler"
        path("v1", "rules", "validate")
    }
}

data class ValidationResult(
    val status: Status,
    val ruleHits: List<RuleInfo>
)

data class RuleInfo(
    val ruleMessage: String
)

enum class Status {
    OK,
    MANUAL_PROCESSING,
    INVALID
}
