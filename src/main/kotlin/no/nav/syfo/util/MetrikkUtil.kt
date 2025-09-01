package no.nav.syfo.util

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.logger
import no.nav.syfo.metrics.ULIK_SENDER_OG_BEHANDLER

fun logUlikBehandler(loggingMeta: LoggingMeta) {
    ULIK_SENDER_OG_BEHANDLER.inc()
    logger.info(
        "Behandlers fnr og avsenders fnr stemmer ikkje {}",
        StructuredArguments.fields(loggingMeta),
    )
}
