package no.nav.syfo.util

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.SamhandlerPraksis
import no.nav.syfo.client.SamhandlerPraksisMatch
import no.nav.syfo.client.SmtssClient
import org.junit.jupiter.api.Test

internal class HandleEmottakSubscriptionTest {

    @Test
    internal fun `Should call start a subscription`() {
        val samhandlerPraksisMatch = mockk<SamhandlerPraksisMatch>()
        val samhandlerPraksis = mockk<SamhandlerPraksis>()
        val partnerReferanse = "12345"

        val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
        val smtssClient = mockk<SmtssClient>()
        val legekontorName = "Test legesenter"
        val signaturFnr = "13134544"
        val msgHead = mockk<XMLMsgHead>()
        val msgId = "21323"
        val loggingMeta = LoggingMeta("1", "", "")

        coEvery { samhandlerPraksis.tss_ident } returns "8000013123"
        coEvery { samhandlerPraksisMatch.percentageMatch } returns 999.99
        coEvery { samhandlerPraksis.samh_praksis_type_kode } returns "FAST"
        coEvery { samhandlerPraksisMatch.samhandlerPraksis } returns samhandlerPraksis

        coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any()) } returns "3123"

        runBlocking {
            handleEmottakSubscription(
                samhandlerPraksisMatch,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
                legekontorName,
                smtssClient,
                signaturFnr,
            )

            coVerify(exactly = 1) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    internal fun `Should not call start a subscription when samhandlerPraksisMatch is null`() {
        val samhandlerPraksisMatch = null
        val partnerReferanse = "12345"
        val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
        val msgHead = mockk<XMLMsgHead>()
        val msgId = "21323"
        val loggingMeta = LoggingMeta("1", "", "")

        val smtssClient = mockk<SmtssClient>()
        val legekontorName = "Test legesenter"
        val signaturFnr = "13134544"

        coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any()) } returns "3123"

        runBlocking {
            handleEmottakSubscription(
                samhandlerPraksisMatch,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
                legekontorName,
                smtssClient,
                signaturFnr,
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    internal fun `Should not call start a subscription when samh_praksis_type_kode is LEVA`() {
        val samhandlerPraksisMatch = mockk<SamhandlerPraksisMatch>()
        val samhandlerPraksis = mockk<SamhandlerPraksis>()
        val partnerReferanse = "12345"
        val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
        val msgHead = mockk<XMLMsgHead>()
        val msgId = "21323"
        val loggingMeta = LoggingMeta("1", "", "")

        val smtssClient = mockk<SmtssClient>()
        val legekontorName = "Test legesenter"
        val signaturFnr = "13134544"

        coEvery { samhandlerPraksis.tss_ident } returns "8000013123"
        coEvery { samhandlerPraksisMatch.percentageMatch } returns 999.99
        coEvery { samhandlerPraksis.samh_praksis_type_kode } returns "LEVA"
        coEvery { samhandlerPraksisMatch.samhandlerPraksis } returns samhandlerPraksis

        coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any()) } returns "3123"

        runBlocking {
            handleEmottakSubscription(
                samhandlerPraksisMatch,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
                legekontorName,
                smtssClient,
                signaturFnr,
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    internal fun `Should not call start a subscription when samh_praksis_type_kode is LEKO`() {
        val samhandlerPraksisMatch = mockk<SamhandlerPraksisMatch>()
        val samhandlerPraksis = mockk<SamhandlerPraksis>()
        val partnerReferanse = "12345"
        val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
        val msgHead = mockk<XMLMsgHead>()
        val msgId = "21323"
        val loggingMeta = LoggingMeta("1", "", "")
        val smtssClient = mockk<SmtssClient>()
        val legekontorName = "Test legesenter"
        val signaturFnr = "13134544"

        coEvery { samhandlerPraksis.tss_ident } returns "8000013123"
        coEvery { samhandlerPraksisMatch.percentageMatch } returns 999.99
        coEvery { samhandlerPraksis.samh_praksis_type_kode } returns "LEKO"
        coEvery { samhandlerPraksisMatch.samhandlerPraksis } returns samhandlerPraksis

        coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any()) } returns "3123"

        runBlocking {
            handleEmottakSubscription(
                samhandlerPraksisMatch,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
                legekontorName,
                smtssClient,
                signaturFnr,
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }

    @Test
    internal fun `Should not call start a subscription when partnerReferanse is null`() {
        val samhandlerPraksisMatch = mockk<SamhandlerPraksisMatch>()
        val samhandlerPraksis = mockk<SamhandlerPraksis>()
        val partnerReferanse = null
        val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
        val msgHead = mockk<XMLMsgHead>()
        val msgId = "21323"
        val loggingMeta = LoggingMeta("1", "", "")
        val smtssClient = mockk<SmtssClient>()
        val legekontorName = "Test legesenter"
        val signaturFnr = "13134544"

        coEvery { samhandlerPraksis.tss_ident } returns "8000013123"
        coEvery { samhandlerPraksisMatch.percentageMatch } returns 999.99
        coEvery { samhandlerPraksis.samh_praksis_type_kode } returns "FALE"
        coEvery { samhandlerPraksisMatch.samhandlerPraksis } returns samhandlerPraksis

        coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any()) } returns "3123"

        runBlocking {
            handleEmottakSubscription(
                samhandlerPraksisMatch,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
                legekontorName,
                smtssClient,
                signaturFnr,
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }
}
