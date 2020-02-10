package no.nav.syfo.util

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet

fun fnrAndDnrIsmissingFromBehandler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.behandler.id.find { it.typeId.v == "FNR" }?.id.isNullOrBlank() &&
                healthInformation.behandler.id.find { it.typeId.v == "DNR" }?.id.isNullOrBlank()

fun medisinskeArsakskodeIsmissing(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligMissingMedisinskeArsakskode(periode.aktivitetIkkeMulig) }

fun arbeidsplassenArsakskodeIsmissing(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligMissingArbeidsplassenArsakskode(periode.aktivitetIkkeMulig) }

fun aktivitetIkkeMuligMissingMedisinskeArsakskode(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null)
        false
    else if (aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode == null)
        true
    else aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode.any { it.v.isNullOrEmpty() }
}

fun aktivitetIkkeMuligMissingArbeidsplassenArsakskode(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null)
        false
    else if (aktivitetIkkeMulig.arbeidsplassen != null && aktivitetIkkeMulig.arbeidsplassen.arsakskode == null)
        true
    else aktivitetIkkeMulig.arbeidsplassen != null && aktivitetIkkeMulig.arbeidsplassen.arsakskode.any { it.v.isNullOrEmpty() }
}

fun annenFraversArsakkodeVIsmissing(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean {
    return when {
        healthInformation.medisinskVurdering == null -> false
        healthInformation.medisinskVurdering.annenFraversArsak == null -> false
        healthInformation.medisinskVurdering.annenFraversArsak.arsakskode == null -> true
        else -> healthInformation.medisinskVurdering.annenFraversArsak.arsakskode.any { it.v.isNullOrEmpty() }
    }
}
