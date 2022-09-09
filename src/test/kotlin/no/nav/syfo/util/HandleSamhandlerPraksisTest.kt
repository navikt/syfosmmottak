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

class HandleSamhandlerPraksisTest : FunSpec({

    context("Check handleSamhandlerPraksis") {
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

            coEvery { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) } returns Unit

            handleSamhandlerPraksis(
                samhandlerPraksisMatch, samhandlerPraksis, receiverBlock, emottakSubscriptionClient, msgHead,
                msgId, loggingMeta
            )

            coVerify(exactly = 1) { emottakSubscriptionClient.startSubscription(any(), any(), any(), any(), any()) }

        }
    }
})