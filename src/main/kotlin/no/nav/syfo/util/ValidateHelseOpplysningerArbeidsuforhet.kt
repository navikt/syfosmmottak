package no.nav.syfo.util

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet

fun fnrOgDnrMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.behandler.id.find { it.typeId.v == "FNR" }?.id.isNullOrBlank() &&
                healthInformation.behandler.id.find { it.typeId.v == "DNR" }?.id.isNullOrBlank()

fun hprMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.behandler.id.find { it.typeId.v == "HPR" }?.id.isNullOrBlank()

fun medisinskeArsakskodeMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligMedisinskeArsakskodeMangler(periode.aktivitetIkkeMulig) }

fun arbeidsplassenArsakskodeMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligArbeidsplassenArsakskodeMangler(periode.aktivitetIkkeMulig) }

fun aktivitetIkkeMuligMedisinskeArsakskodeMangler(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null)
        false
    else if (aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode == null)
        true
    else aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode.any { it.v.isNullOrEmpty() }
}

fun aktivitetIkkeMuligArbeidsplassenArsakskodeMangler(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null)
        false
    else if (aktivitetIkkeMulig.arbeidsplassen != null && aktivitetIkkeMulig.arbeidsplassen.arsakskode == null)
        true
    else aktivitetIkkeMulig.arbeidsplassen != null && aktivitetIkkeMulig.arbeidsplassen.arsakskode.any { it.v.isNullOrEmpty() }
}

fun annenFraversArsakkodeVMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean {
    return when {
        healthInformation.medisinskVurdering == null -> false
        healthInformation.medisinskVurdering.annenFraversArsak == null -> false
        healthInformation.medisinskVurdering.annenFraversArsak.arsakskode == null -> true
        else -> healthInformation.medisinskVurdering.annenFraversArsak.arsakskode.any { it.v.isNullOrEmpty() }
    }
}
