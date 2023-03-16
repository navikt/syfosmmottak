package no.nav.syfo.util

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.ArbeidsrelatertArsakType
import no.nav.syfo.model.MedisinskArsakType

fun fnrOgDnrMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
    healthInformation.behandler.id.find { it.typeId.v == "FNR" }?.id.isNullOrBlank() &&
        healthInformation.behandler.id.find { it.typeId.v == "DNR" }?.id.isNullOrBlank()

fun hprMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
    healthInformation.behandler.id.find { it.typeId.v == "HPR" }?.id.isNullOrBlank()

fun medisinskeArsakskodeMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
    healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligMedisinskeArsakskodeMangler(periode.aktivitetIkkeMulig) }

fun medisinskeArsakskodeHarUgyldigVerdi(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
    healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligMedisinskeArsakskodeHarUgyldigVerdi(periode.aktivitetIkkeMulig) }

fun arbeidsplassenArsakskodeMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
    healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligArbeidsplassenArsakskodeMangler(periode.aktivitetIkkeMulig) }

fun arbeidsplassenArsakskodeHarUgyldigVerdi(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
    healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligArbeidsplassenArsakskodeHarUgyldigVerdi(periode.aktivitetIkkeMulig) }

fun aktivitetIkkeMuligMedisinskeArsakskodeMangler(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null) {
        false
    } else if (aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode == null) {
        true
    } else {
        aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode.any { it.v.isNullOrEmpty() }
    }
}

fun aktivitetIkkeMuligMedisinskeArsakskodeHarUgyldigVerdi(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null) {
        false
    } else {
        aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode.any { code -> MedisinskArsakType.values().firstOrNull { code.v.trim() == it.codeValue } == null }
    }
}

fun aktivitetIkkeMuligArbeidsplassenArsakskodeMangler(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null) {
        false
    } else if (aktivitetIkkeMulig.arbeidsplassen != null && aktivitetIkkeMulig.arbeidsplassen.arsakskode == null) {
        true
    } else {
        aktivitetIkkeMulig.arbeidsplassen != null && aktivitetIkkeMulig.arbeidsplassen.arsakskode.any { it.v.isNullOrEmpty() }
    }
}

fun aktivitetIkkeMuligArbeidsplassenArsakskodeHarUgyldigVerdi(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null) {
        false
    } else {
        aktivitetIkkeMulig.arbeidsplassen != null && aktivitetIkkeMulig.arbeidsplassen.arsakskode.any { code -> ArbeidsrelatertArsakType.values().firstOrNull { code.v.trim() == it.codeValue } == null }
    }
}

fun annenFraversArsakkodeVMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean {
    return when {
        healthInformation.medisinskVurdering == null -> false
        healthInformation.medisinskVurdering.annenFraversArsak == null -> false
        healthInformation.medisinskVurdering.annenFraversArsak.arsakskode == null -> true
        else -> healthInformation.medisinskVurdering.annenFraversArsak.arsakskode.any { it.v.isNullOrEmpty() }
    }
}

fun periodetypeIkkeAngitt(aktivitet: HelseOpplysningerArbeidsuforhet.Aktivitet): Boolean {
    return aktivitet.periode.any {
        it.aktivitetIkkeMulig == null && it.gradertSykmelding == null && it.avventendeSykmelding == null &&
            it.behandlingsdager == null && it.isReisetilskudd != true
    }
}

fun behandletDatoMangler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean {
    return healthInformation.kontaktMedPasient.behandletDato == null
}
