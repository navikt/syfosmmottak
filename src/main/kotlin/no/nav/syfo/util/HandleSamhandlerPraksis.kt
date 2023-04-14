package no.nav.syfo.util

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.log
import no.nav.syfo.metrics.IKKE_OPPDATERT_PARTNERREG

suspend fun handleEmottakSubscription(
    tssId: String?,
    emottakSubscriptionClient: EmottakSubscriptionClient,
    msgHead: XMLMsgHead,
    msgId: String,
    partnerreferanse: String?,
    loggingMeta: LoggingMeta,
) {
    if (tssId == null) {
        log.info(
            "SamhandlerPraksis is Not found, partnerreferanse: $partnerreferanse {}",
            StructuredArguments.fields(loggingMeta),
        )
        IKKE_OPPDATERT_PARTNERREG.inc()
    } else if (
        !partnerreferanse.isNullOrEmpty() &&
        partnerreferanse.isNotBlank()
    ) {
        emottakSubscriptionClient.startSubscription(
            tssId,
            msgHead,
            partnerreferanse,
            msgId,
            loggingMeta,
        )
    } else {
        log.info(
            "PartnerReferanse is empty or blank, subscription_emottak is not created,partnerreferanse: $partnerreferanse {}",
            StructuredArguments.fields(loggingMeta),
        )
        IKKE_OPPDATERT_PARTNERREG.inc()
    }
}
