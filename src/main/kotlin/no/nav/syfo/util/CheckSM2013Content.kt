package no.nav.syfo.util

import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.handlestatus.handleAktivitetOrPeriodeIsMissing
import no.nav.syfo.handlestatus.handleAnnenFraversArsakkodeVIsmissing
import no.nav.syfo.handlestatus.handleArbeidsgiverUgyldigVerdi
import no.nav.syfo.handlestatus.handleArbeidsplassenArsakskodeHarUgyldigVerdi
import no.nav.syfo.handlestatus.handleArbeidsplassenArsakskodeIsmissing
import no.nav.syfo.handlestatus.handleBehandletDatoMangler
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeBeskrivelseMissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeIsMissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeVerkIsMissing
import no.nav.syfo.handlestatus.handleDoctorNotFoundInPDL
import no.nav.syfo.handlestatus.handleFnrAndDnrAndHprIsmissingFromBehandler
import no.nav.syfo.handlestatus.handleHovedDiagnoseDiagnoseBeskrivelseMissing
import no.nav.syfo.handlestatus.handleHovedDiagnoseDiagnosekodeMissing
import no.nav.syfo.handlestatus.handleMedisinskeArsakskodeHarUgyldigVerdi
import no.nav.syfo.handlestatus.handleMedisinskeArsakskodeIsmissing
import no.nav.syfo.handlestatus.handlePatientNotFoundInPDL
import no.nav.syfo.handlestatus.handlePeriodetypeMangler
import no.nav.syfo.handlestatus.handleTestFnrInProd
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.service.DuplicationService
import org.apache.kafka.clients.producer.KafkaProducer

fun checkSM2013Content(
    pasientFnr: PdlPerson?,
    signerendeAktorId: String?,
    healthInformation: HelseOpplysningerArbeidsuforhet,
    originaltPasientFnr: String,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    ediLoggId: String,
    msgId: String,
    msgHead: XMLMsgHead,
    env: EnvironmentVariables,
    kafkaproducerApprec: KafkaProducer<String, Apprec>,
    duplicationService: DuplicationService,
    duplicateCheck: DuplicateCheck,
): Boolean {
    if (pasientFnr?.aktorId == null || pasientFnr.fnr == null) {
        handlePatientNotFoundInPDL(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (signerendeAktorId == null) {
        handleDoctorNotFoundInPDL(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (
        healthInformation.aktivitet == null || healthInformation.aktivitet.periode.isNullOrEmpty()
    ) {
        handleAktivitetOrPeriodeIsMissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (periodetypeIkkeAngitt(healthInformation.aktivitet)) {
        handlePeriodetypeMangler(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (
        healthInformation.medisinskVurdering?.biDiagnoser != null &&
            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any {
                it.v.isNullOrEmpty()
            }
    ) {
        handleBiDiagnoserDiagnosekodeIsMissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (
        healthInformation.medisinskVurdering?.biDiagnoser != null &&
            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any {
                it.s.isNullOrEmpty()
            }
    ) {
        handleBiDiagnoserDiagnosekodeVerkIsMissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (
        healthInformation.medisinskVurdering?.biDiagnoser != null &&
            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any {
                it.dn.isNullOrEmpty()
            }
    ) {
        handleBiDiagnoserDiagnosekodeBeskrivelseMissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (fnrOgDnrMangler(healthInformation) && hprMangler(healthInformation)) {
        handleFnrAndDnrAndHprIsmissingFromBehandler(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (
        healthInformation.medisinskVurdering?.hovedDiagnose?.diagnosekode != null &&
            healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v == null
    ) {
        handleHovedDiagnoseDiagnosekodeMissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (
        healthInformation.medisinskVurdering?.hovedDiagnose?.diagnosekode != null &&
            healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.dn == null
    ) {
        handleHovedDiagnoseDiagnoseBeskrivelseMissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (medisinskeArsakskodeMangler(healthInformation)) {
        handleMedisinskeArsakskodeIsmissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (medisinskeArsakskodeHarUgyldigVerdi(healthInformation)) {
        handleMedisinskeArsakskodeHarUgyldigVerdi(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (arbeidsplassenArsakskodeMangler(healthInformation)) {
        handleArbeidsplassenArsakskodeIsmissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (arbeidsplassenArsakskodeHarUgyldigVerdi(healthInformation)) {
        handleArbeidsplassenArsakskodeHarUgyldigVerdi(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (erTestFnr(originaltPasientFnr) && env.cluster == "prod-gcp") {
        handleTestFnrInProd(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    if (annenFraversArsakkodeVMangler(healthInformation)) {
        handleAnnenFraversArsakkodeVIsmissing(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }
    if (behandletDatoMangler(healthInformation)) {
        handleBehandletDatoMangler(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }
    if (arbeidsgiverUgyldigVerdi(healthInformation)) {
        handleArbeidsgiverUgyldigVerdi(
            loggingMeta,
            fellesformat,
            ediLoggId,
            msgId,
            msgHead,
            env,
            kafkaproducerApprec,
            duplicationService,
            duplicateCheck,
        )
        return true
    }

    return false
}
