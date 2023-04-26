package no.nav.syfo.util

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.SmtssClient
import org.junit.jupiter.api.Test

internal class HandleEmottakSubscriptionTest {

    @Test
    internal fun `Should call start a subscription`() {
        val partnerReferanse = "12345"

        val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
        val smtssClient = mockk<SmtssClient>()
        val legekontorName = "Test legesenter"
        val signaturFnr = "13134544"
        val msgHead = mockk<XMLMsgHead>()
        val msgId = "21323"
        val loggingMeta = LoggingMeta("1", "", "")
        val sykmeldingsId = "131-1--213-223-23"

        coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any(), any()) } returns "8000013123"

        runBlocking {
            val tssId = smtssClient.findBestTssIdEmottak(signaturFnr, legekontorName, loggingMeta, sykmeldingsId)

            handleEmottakSubscription(
                tssId,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
            )

            coVerify(exactly = 1) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    internal fun `Should not call start a subscription when samhandlerPraksisMatch is null`() {
        val partnerReferanse = "12345"
        val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
        val msgHead = mockk<XMLMsgHead>()
        val msgId = "21323"
        val loggingMeta = LoggingMeta("1", "", "")

        val smtssClient = mockk<SmtssClient>()
        val legekontorName = "Test legesenter"
        val signaturFnr = "13134544"
        val sykmeldingsId = "131-1--213-223-23"

        coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any(), any()) } returns null

        runBlocking {
            val tssId = smtssClient.findBestTssIdEmottak(signaturFnr, legekontorName, loggingMeta, sykmeldingsId)

            handleEmottakSubscription(
                tssId,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    internal fun `Should not call start a subscription when partnerReferanse is null`() {
        val partnerReferanse = null
        val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
        val msgHead = mockk<XMLMsgHead>()
        val msgId = "21323"
        val loggingMeta = LoggingMeta("1", "", "")
        val smtssClient = mockk<SmtssClient>()
        val legekontorName = "Test legesenter"
        val signaturFnr = "13134544"
        val sykmeldingsId = "131-1--213-223-23"

        coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any(), any()) } returns "8000013123"

        runBlocking {
            val tssId = smtssClient.findBestTssIdEmottak(signaturFnr, legekontorName, loggingMeta, sykmeldingsId)
            handleEmottakSubscription(
                tssId,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }
}
