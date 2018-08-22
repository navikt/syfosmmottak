package no.nav.syfo

import net.logstash.logback.argument.StructuredArguments
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.io.IOException

private val log = LoggerFactory.getLogger(SyfoSykemelginReglerClient::class.java)

class SyfoSykemelginReglerClient(private val url: String, private val username: String, private val password: String) {
    private val client: OkHttpClient = OkHttpClient()

    fun getSamhandler(ident: String): List<Samhandler> {
        val request = Request.Builder()
                .get()
                .header("Authorization", Credentials.basic(username, password))
                .url(HttpUrl.parse(url)!!
                        .newBuilder()
                        .addPathSegments("/rest/sar/samh")
                        .addQueryParameter("ident", ident)
                        .build()
                )
                .build()

        val response = client.newCall(request)
                .execute()
        if (response.isSuccessful) {
            return objectMapper.readValue(response.body()?.byteStream(), Array<Samhandler>::class.java).toList()
        } else {
            log.error("Received an error while contacting SyfoSykemelingRegler {}", StructuredArguments.keyValue("errorBody", response.body()?.string()))
            throw IOException("Unable to contact SyfoSykemelingRegler, got status code ${response.code()}")
        }
    }
}

data class Samhandler(
    val samh_id: String

)
