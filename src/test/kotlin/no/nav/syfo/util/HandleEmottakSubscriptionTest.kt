package no.nav.syfo.util

import io.kotest.core.spec.style.FunSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.SamhandlerPraksis
import no.nav.syfo.client.SamhandlerPraksisMatch

class HandleEmottakSubscriptionTest : FunSpec({

    context("Check handleEmottakSubscription function") {
        test("Should call start a subscription") {
            val samhandlerPraksisMatch = mockk<SamhandlerPraksisMatch>()
            val samhandlerPraksis = mockk<SamhandlerPraksis>()
            val receiverBlock = mockk<XMLMottakenhetBlokk>()
            val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
            val msgHead = mockk<XMLMsgHead>()
            val msgId = "21323"
            val loggingMeta = LoggingMeta("1", "", "")

            coEvery { samhandlerPraksis.tss_ident } returns "8000013123"
            coEvery { samhandlerPraksisMatch.percentageMatch } returns 999.99
            coEvery { samhandlerPraksis.samh_praksis_type_kode } returns "FAST"
            coEvery { receiverBlock.partnerReferanse } returns "12345"
            coEvery { samhandlerPraksisMatch.samhandlerPraksis } returns samhandlerPraksis

            coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit

            handleEmottakSubscription(
                samhandlerPraksisMatch, receiverBlock, emottakSubscriptionClient, msgHead,
                msgId, loggingMeta
            )

            coVerify(exactly = 1) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }

        test("Should not call start a subscription when samhandlerPraksisMatch is null") {
            val samhandlerPraksisMatch = null
            val receiverBlock = mockk<XMLMottakenhetBlokk>()
            val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
            val msgHead = mockk<XMLMsgHead>()
            val msgId = "21323"
            val loggingMeta = LoggingMeta("1", "", "")

            coEvery { receiverBlock.partnerReferanse } returns "12345"
            coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit

            handleEmottakSubscription(
                samhandlerPraksisMatch, receiverBlock, emottakSubscriptionClient, msgHead,
                msgId, loggingMeta
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }

        test("Should not call start a subscription when samh_praksis_type_kode is LEVA") {
            val samhandlerPraksisMatch = mockk<SamhandlerPraksisMatch>()
            val samhandlerPraksis = mockk<SamhandlerPraksis>()
            val receiverBlock = mockk<XMLMottakenhetBlokk>()
            val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
            val msgHead = mockk<XMLMsgHead>()
            val msgId = "21323"
            val loggingMeta = LoggingMeta("1", "", "")

            coEvery { samhandlerPraksis.tss_ident } returns "8000013123"
            coEvery { samhandlerPraksisMatch.percentageMatch } returns 999.99
            coEvery { samhandlerPraksis.samh_praksis_type_kode } returns "LEVA"
            coEvery { receiverBlock.partnerReferanse } returns "12345"
            coEvery { samhandlerPraksisMatch.samhandlerPraksis } returns samhandlerPraksis

            coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit

            handleEmottakSubscription(
                samhandlerPraksisMatch, receiverBlock, emottakSubscriptionClient, msgHead,
                msgId, loggingMeta
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }

        test("Should not call start a subscription when samh_praksis_type_kode is LEKO") {
            val samhandlerPraksisMatch = mockk<SamhandlerPraksisMatch>()
            val samhandlerPraksis = mockk<SamhandlerPraksis>()
            val receiverBlock = mockk<XMLMottakenhetBlokk>()
            val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
            val msgHead = mockk<XMLMsgHead>()
            val msgId = "21323"
            val loggingMeta = LoggingMeta("1", "", "")

            coEvery { samhandlerPraksis.tss_ident } returns "8000013123"
            coEvery { samhandlerPraksisMatch.percentageMatch } returns 999.99
            coEvery { samhandlerPraksis.samh_praksis_type_kode } returns "LEKO"
            coEvery { receiverBlock.partnerReferanse } returns "12345"
            coEvery { samhandlerPraksisMatch.samhandlerPraksis } returns samhandlerPraksis

            coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit

            handleEmottakSubscription(
                samhandlerPraksisMatch, receiverBlock, emottakSubscriptionClient, msgHead,
                msgId, loggingMeta
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }

        test("Should not call start a subscription when partnerReferanse is null") {
            val samhandlerPraksisMatch = mockk<SamhandlerPraksisMatch>()
            val samhandlerPraksis = mockk<SamhandlerPraksis>()
            val receiverBlock = mockk<XMLMottakenhetBlokk>()
            val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
            val msgHead = mockk<XMLMsgHead>()
            val msgId = "21323"
            val loggingMeta = LoggingMeta("1", "", "")

            coEvery { samhandlerPraksis.tss_ident } returns "8000013123"
            coEvery { samhandlerPraksisMatch.percentageMatch } returns 999.99
            coEvery { samhandlerPraksis.samh_praksis_type_kode } returns "FALE"
            coEvery { receiverBlock.partnerReferanse } returns null
            coEvery { samhandlerPraksisMatch.samhandlerPraksis } returns samhandlerPraksis

            coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit

            handleEmottakSubscription(
                samhandlerPraksisMatch, receiverBlock, emottakSubscriptionClient, msgHead,
                msgId, loggingMeta
            )

            coVerify(exactly = 0) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }
        }
    }
})
