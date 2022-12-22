package no.nav.syfo.service

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.duplicationcheck.db.extractDuplicationCheckByMottakId
import no.nav.syfo.duplicationcheck.db.extractDuplicationCheckBySha256HealthInformation
import no.nav.syfo.duplicationcheck.db.persistSha256
import no.nav.syfo.duplicationcheck.model.DuplicationCheck
import no.nav.syfo.objectMapper
import java.security.MessageDigest
import no.nav.syfo.duplicationcheck.db.persistDuplicateMessage
import no.nav.syfo.duplicationcheck.model.Duplicate

class DuplicationService(private val database: DatabaseInterface) {
    fun persistDuplicationCheck(
        duplicationCheck: DuplicationCheck
    ) {
        database.persistSha256(duplicationCheck)
    }

    fun persistDuplication(
        duplicate: Duplicate
    ) {
        database.persistDuplicateMessage(duplicate)
    }

    fun getDuplicationCheck(sha256HealthInformation: String, mottakId: String): DuplicationCheck? {
        val duplicationCheckSha256HealthInformation =
            database.extractDuplicationCheckBySha256HealthInformation(sha256HealthInformation)
        if (duplicationCheckSha256HealthInformation != null) {
            return duplicationCheckSha256HealthInformation
        } else {
            val duplicationCheckMottakId = getLatestDuplicationCheck(
                database.extractDuplicationCheckByMottakId(mottakId)
            )
            if (duplicationCheckMottakId != null) {
                return duplicationCheckMottakId
            }
            return null
        }
    }
}

fun getLatestDuplicationCheck(duplicationChecks: List<DuplicationCheck>): DuplicationCheck? {
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
