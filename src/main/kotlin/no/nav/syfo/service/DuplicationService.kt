package no.nav.syfo.service

import java.security.MessageDigest
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.duplicationcheck.db.extractDuplicateCheckByMottakId
import no.nav.syfo.duplicationcheck.db.extractDuplicateCheckBySha256HealthInformation
import no.nav.syfo.duplicationcheck.db.persistDuplicateCheck
import no.nav.syfo.duplicationcheck.db.persistDuplicateMessage
import no.nav.syfo.duplicationcheck.model.Duplicate
import no.nav.syfo.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.objectMapper

class DuplicationService(private val database: DatabaseInterface) {
    fun persistDuplicationCheck(
        duplicateCheck: DuplicateCheck,
    ) {
        database.persistDuplicateCheck(duplicateCheck)
    }

    fun persistDuplication(
        duplicate: Duplicate,
    ) {
        database.persistDuplicateMessage(duplicate)
    }

    fun getDuplicationCheck(sha256HealthInformation: String, mottakId: String): DuplicateCheck? {
        val duplicationCheckSha256HealthInformation =
            database.extractDuplicateCheckBySha256HealthInformation(sha256HealthInformation)
        if (duplicationCheckSha256HealthInformation != null) {
            return duplicationCheckSha256HealthInformation
        } else {
            val duplicationCheckMottakId =
                getLatestDuplicationCheck(
                    database.extractDuplicateCheckByMottakId(mottakId),
                )
            if (duplicationCheckMottakId != null) {
                return duplicationCheckMottakId
            }
            return null
        }
    }
}

fun getLatestDuplicationCheck(duplicationChecks: List<DuplicateCheck>): DuplicateCheck? {
    val latest = duplicationChecks.maxByOrNull { it.mottattDate }
    return when (latest) {
        null -> null
        else -> latest
    }
}

fun sha256hashstring(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): String =
    MessageDigest.getInstance("SHA-256")
        .digest(objectMapper.writeValueAsBytes(helseOpplysningerArbeidsuforhet))
        .fold("") { str, it -> str + "%02x".format(it) }
