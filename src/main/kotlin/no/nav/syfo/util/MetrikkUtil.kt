package no.nav.syfo.util

import no.nav.syfo.metrics.GEN_COUNTER
import no.nav.syfo.model.MedisinskVurdering

fun countGenMetrikk(medisinskVurdering: MedisinskVurdering) {
    if (medisinskVurdering.hovedDiagnose != null) {
        val coronavirusCodes = listOf("R991", "U071")
        if (coronavirusCodes.contains(medisinskVurdering.hovedDiagnose!!.kode)) {
            GEN_COUNTER.inc()
        } else if (!medisinskVurdering.biDiagnoser.isNullOrEmpty()) {
            if (medisinskVurdering.biDiagnoser.find { coronavirusCodes.contains(it.kode) } != null) {
                GEN_COUNTER.inc()
            }
        }
    }
}
