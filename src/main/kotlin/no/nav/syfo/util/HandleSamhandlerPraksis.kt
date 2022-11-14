package no.nav.syfo.util

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.SamhandlerPraksis
import no.nav.syfo.client.SamhandlerPraksisMatch
import no.nav.syfo.client.samhandlerpraksisIsLegevakt
import no.nav.syfo.log
import no.nav.syfo.metrics.IKKE_OPPDATERT_PARTNERREG
import no.nav.syfo.metrics.MANGLER_TSSIDENT

suspend fun handleEmottakSubscription(
    samhandlerPraksisMatch: SamhandlerPraksisMatch?,
    receiverBlock: XMLMottakenhetBlokk,
    emottakSubscriptionClient: EmottakSubscriptionClient,
    msgHead: XMLMsgHead,
    msgId: String,
    loggingMeta: LoggingMeta
) {

    if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
        log.info(
            "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created, {}",
            StructuredArguments.fields(loggingMeta)
        )
        IKKE_OPPDATERT_PARTNERREG.inc()
    } else {
        when (samhandlerPraksisMatch?.samhandlerPraksis) {
            null -> {
                log.info("SamhandlerPraksis is Not found, {}", StructuredArguments.fields(loggingMeta))
                IKKE_OPPDATERT_PARTNERREG.inc()
            }

            else -> if (!samhandlerpraksisIsLegevakt(samhandlerPraksisMatch.samhandlerPraksis) &&
                !receiverBlock.partnerReferanse.isNullOrEmpty() &&
                receiverBlock.partnerReferanse.isNotBlank()
            ) {
                emottakSubscriptionClient.startSubscription(
                    samhandlerPraksisMatch.samhandlerPraksis,
                    msgHead,
                    receiverBlock,
                    msgId,
                    loggingMeta
                )
            } else {
                if (!receiverBlock.partnerReferanse.isNullOrEmpty() &&
                    receiverBlock.partnerReferanse.isNotBlank()
                ) {
                    log.info(
                        "PartnerReferanse is empty or blank, subscription_emottak is not created, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                } else {
                    log.info(
                        "SamhandlerPraksis is Legevakt, subscription_emottak is not created, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                }
                IKKE_OPPDATERT_PARTNERREG.inc()
            }
        }
    }
}
