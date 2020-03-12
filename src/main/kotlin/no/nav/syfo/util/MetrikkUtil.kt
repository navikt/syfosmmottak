package no.nav.syfo.util

import no.nav.syfo.metrics.NEW_DIAGNOSE_COUNTER
import no.nav.syfo.model.MedisinskVurdering

fun countNewDiagnoseCode(medisinskVurdering: MedisinskVurdering) {
    val newDiagnoseCode = listOf("R991", "U071")
    if (medisinskVurdering.hovedDiagnose != null && newDiagnoseCode.contains(medisinskVurdering.hovedDiagnose!!.kode)) {
        NEW_DIAGNOSE_COUNTER.inc()
    } else if (!medisinskVurdering.biDiagnoser.isNullOrEmpty() && medisinskVurdering.biDiagnoser.find { newDiagnoseCode.contains(it.kode) } != null) {
        NEW_DIAGNOSE_COUNTER.inc()
    }
}
