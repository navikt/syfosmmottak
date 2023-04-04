package no.nav.syfo.util

import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.SamhandlerPraksisMatch
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.samhandlerpraksisIsLegevakt
import no.nav.syfo.log
import no.nav.syfo.metrics.IKKE_OPPDATERT_PARTNERREG

suspend fun handleEmottakSubscription(
    samhandlerPraksisMatch: SamhandlerPraksisMatch?,
    emottakSubscriptionClient: EmottakSubscriptionClient,
    msgHead: XMLMsgHead,
    msgId: String,
    partnerreferanse: String?,
    loggingMeta: LoggingMeta,
    legekontorOrgName: String,
    smtssClient: SmtssClient,
    signaturFnr: String,
) {
    if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
        log.info(
            "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created," +
                "partnerreferanse: $partnerreferanse, {}",
            StructuredArguments.fields(loggingMeta),
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
                try {
                    if (legekontorOrgName.isNotEmpty()) {
                        val tssId = smtssClient.findBestTssIdEmottak(signaturFnr, legekontorOrgName, loggingMeta)

                        if (samhandlerPraksisMatch.samhandlerPraksis.tss_ident == tssId) {
                            log.info("Found samme tssid {}", StructuredArguments.fields(loggingMeta))
                        } else {
                            log.info(
                                "Found different tssids." +
                                    " Kuhr tssid ${samhandlerPraksisMatch.samhandlerPraksis.tss_ident}, smtss $tssId {}",
                                StructuredArguments.fields(loggingMeta),
                            )
                        }
                    } else {
                        log.info("legekontorOrgName is null or empty {}", StructuredArguments.fields(loggingMeta))
                    }
                } catch (exception: Exception) {
                    log.warn("smtss call failed due to: ${exception.message} {}", StructuredArguments.fields(loggingMeta))
                }

                emottakSubscriptionClient.startSubscription(
                    samhandlerPraksisMatch.samhandlerPraksis,
                    msgHead,
                    partnerreferanse,
                    msgId,
                    loggingMeta,
                )
            } else {
                if (!partnerreferanse.isNullOrEmpty() &&
                    partnerreferanse.isNotBlank()
                ) {
                    log.info(
                        "PartnerReferanse is empty or blank, subscription_emottak is not created,partnerreferanse: $partnerreferanse {}",
                        StructuredArguments.fields(loggingMeta),
                    )
                } else {
                    log.info(
                        "SamhandlerPraksis is Legevakt, subscription_emottak is not created,partnerreferanse: $partnerreferanse {}",
                        StructuredArguments.fields(loggingMeta),
                    )
                }
                IKKE_OPPDATERT_PARTNERREG.inc()
            }
        }
    }
}
