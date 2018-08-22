package no.nav.syfo

import net.logstash.logback.argument.StructuredArguments
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import java.io.IOException

private val log = LoggerFactory.getLogger(SyfoSykemelginReglerClient::class.java)

class SyfoSykemelginReglerClient(private val url: String, private val username: String, private val password: String) {
    private val client: OkHttpClient = OkHttpClient()

    fun executeRuleValidation(data: String): ValidationResult {
        val request = Request.Builder()
                .post(RequestBody.create(MediaType.parse("application/json"), data))
                .header("Authorization", Credentials.basic(username, password))
                .url(HttpUrl.parse(url)!!
                        .newBuilder()
                        .addPathSegments("/v1/rules/validate")
                        .build()
                )
                .build()

        val response = client.newCall(request)
                .execute()
        if (response.isSuccessful) {
            return objectMapper.readValue(response.body()?.byteStream(), ValidationResult::class.java)
        } else {
            log.error("Received an error while contacting SyfoSykemelingRegler {}", StructuredArguments.keyValue("errorBody", response.body()?.string()))
            throw IOException("Unable to contact SyfoSykemelingRegler, got status code ${response.code()}")
        }
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
