package no.nav.syfo.util

import no.nav.syfo.metrics.CORONA_COUNTER
import no.nav.syfo.model.MedisinskVurdering

fun countCorona(medisinskVurdering: MedisinskVurdering) {
    if (medisinskVurdering.hovedDiagnose != null) {
        val coronavirusCodes = listOf("R991", "U071")
        if (coronavirusCodes.contains(medisinskVurdering.hovedDiagnose!!.kode)) {
            CORONA_COUNTER.inc()
        } else if (!medisinskVurdering.biDiagnoser.isNullOrEmpty()) {
            if (medisinskVurdering.biDiagnoser.find { coronavirusCodes.contains(it.kode) } != null) {
                CORONA_COUNTER.inc()
            }
        }
    }
}
