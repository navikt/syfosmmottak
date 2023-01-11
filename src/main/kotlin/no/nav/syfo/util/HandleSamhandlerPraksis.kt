package no.nav.syfo.util

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.SamhandlerPraksisMatch
import no.nav.syfo.client.samhandlerpraksisIsLegevakt
import no.nav.syfo.log
import no.nav.syfo.metrics.IKKE_OPPDATERT_PARTNERREG

suspend fun handleEmottakSubscription(
    samhandlerPraksisMatch: SamhandlerPraksisMatch?,
    emottakSubscriptionClient: EmottakSubscriptionClient,
    msgHead: XMLMsgHead,
    msgId: String,
    partnerreferanse: String?,
    loggingMeta: LoggingMeta
) {

    if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
        log.info(
            "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created," +
                "partnerreferanse: $partnerreferanse, {}",
            StructuredArguments.fields(loggingMeta)
        )
        IKKE_OPPDATERT_PARTNERREG.inc()
    } else {
        when (samhandlerPraksisMatch?.samhandlerPraksis) {
            null -> {
                log.info("SamhandlerPraksis is Not found, partnerreferanse: $partnerreferanse {}", StructuredArguments.fields(loggingMeta))
                IKKE_OPPDATERT_PARTNERREG.inc()
            }

            else -> if (!samhandlerpraksisIsLegevakt(samhandlerPraksisMatch.samhandlerPraksis) &&
                !partnerreferanse.isNullOrEmpty() &&
                partnerreferanse.isNotBlank()
            ) {
                emottakSubscriptionClient.startSubscription(
                    samhandlerPraksisMatch.samhandlerPraksis,
                    msgHead,
                    partnerreferanse,
                    msgId,
                    loggingMeta
                )
            } else {
                if (!partnerreferanse.isNullOrEmpty() &&
                    partnerreferanse.isNotBlank()
                ) {
                    log.info(
                        "PartnerReferanse is empty or blank, subscription_emottak is not created,partnerreferanse: $partnerreferanse {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                } else {
                    log.info(
                        "SamhandlerPraksis is Legevakt, subscription_emottak is not created,partnerreferanse: $partnerreferanse {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                }
                IKKE_OPPDATERT_PARTNERREG.inc()
            }
        }
    }
}
