package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.io.ByteArrayOutputStream
import java.io.IOException
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender
import no.nav.syfo.logger
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.senderMarshaller

class EmottakSubscriptionClient(
    private val endpointUrl: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient,
) {
    // This functionality is only necessary due to sending out dialogMelding and oppfølgingsplan to
    // doctor
    suspend fun startSubscription(
        tssIdent: String,
        msgHead: XMLMsgHead,
        partnerreferanse: String,
        msgId: String,
        loggingMeta: LoggingMeta,
    ) {
        logger.info(
            "Update subscription emottak for tssid: $tssIdent {}",
            StructuredArguments.fields(loggingMeta),
        )
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        try {
            httpClient.post("$endpointUrl/emottak/startsubscription") {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $accessToken")
                header("Nav-Call-Id", msgId)
                setBody(
                    StartSubscriptionRequest(
                        tssIdent = tssIdent,
                        sender = convertSenderToBase64(msgHead.msgInfo.sender),
                        partnerreferanse = partnerreferanse.toInt(),
                    ),
                )
            }
            logger.info(
                "Started subscription for tss: $tssIdent and partnerRef: $partnerreferanse, msgId: $msgId"
            )
        } catch (exception: Exception) {
            logger.error(
                "Couldn't update emottak subscription due to error: ${exception}, msgId: $msgId"
            )
            throw IOException(
                "Vi fikk en uventet feil fra smgcp, prøver på nytt! ${exception.message}, msgId: $msgId"
            )
        }
    }

    private fun convertSenderToBase64(sender: XMLSender): ByteArray =
        ByteArrayOutputStream()
            .use {
                senderMarshaller.marshal(sender, it)
                it
            }
            .toByteArray()
}

data class StartSubscriptionRequest(
    val tssIdent: String,
    val sender: ByteArray,
    val partnerreferanse: Int,
)
