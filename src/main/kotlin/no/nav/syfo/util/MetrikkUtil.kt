package no.nav.syfo.util

import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.logger
import no.nav.syfo.metrics.NEW_DIAGNOSE_COUNTER
import no.nav.syfo.metrics.ULIK_SENDER_OG_BEHANDLER
import no.nav.syfo.model.MedisinskVurdering

fun countNewDiagnoseCode(medisinskVurdering: MedisinskVurdering) {
    val newDiagnoseCode = listOf("R991", "U071")
    if (
        medisinskVurdering.hovedDiagnose != null &&
            newDiagnoseCode.contains(medisinskVurdering.hovedDiagnose!!.kode)
    ) {
        NEW_DIAGNOSE_COUNTER.inc()
    } else if (
        !medisinskVurdering.biDiagnoser.isNullOrEmpty() &&
            medisinskVurdering.biDiagnoser.find { newDiagnoseCode.contains(it.kode) } != null
    ) {
        NEW_DIAGNOSE_COUNTER.inc()
    }
}

fun logUlikBehandler(loggingMeta: LoggingMeta) {
    ULIK_SENDER_OG_BEHANDLER.inc()
    logger.info(
        "Behandlers fnr og avsenders fnr stemmer ikkje {}",
        StructuredArguments.fields(loggingMeta),
    )
}
