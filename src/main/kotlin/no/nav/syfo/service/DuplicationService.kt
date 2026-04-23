package no.nav.syfo.service

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.security.MessageDigest
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.db.DatabaseInterface
import no.nav.syfo.duplicationcheck.db.extractDuplicateCheckByMottakId
import no.nav.syfo.duplicationcheck.db.extractDuplicateCheckBySha256HealthInformation
import no.nav.syfo.duplicationcheck.db.persistDuplicateCheck
import no.nav.syfo.duplicationcheck.db.persistDuplicateMessage
import no.nav.syfo.duplicationcheck.model.Duplicate
import no.nav.syfo.duplicationcheck.model.DuplicateCheck

abstract class UtenStrekkode {
    @get:JsonIgnore abstract val strekkode: String
}

private val sha256ObjectMapper: ObjectMapper =
    ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .addMixIn(HelseOpplysningerArbeidsuforhet::class.java, UtenStrekkode::class.java)

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

    fun getDuplicationCheck(sha256Result: Sha256Result, mottakId: String): DuplicateCheck? {
        val duplicationCheckSha256HealthInformation =
            database.extractDuplicateCheckBySha256HealthInformation(
                sha256Result.sha256HealthInformation,
                sha256Result.sha256WithSigner,
            )

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

data class Sha256Result(
    val sha256HealthInformation: String,
    val sha256WithSigner: String,
)

fun sha256hashstring(
    helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet,
    signerHpr: String?,
): Sha256Result {
    val healthInfoBytes = sha256ObjectMapper.writeValueAsBytes(helseOpplysningerArbeidsuforhet)

    val sha256HealthInformation =
        MessageDigest.getInstance("SHA-256").digest(healthInfoBytes).toHexString()

    val sha256WithSigner =
        when (signerHpr) {
            null -> sha256HealthInformation
            else ->
                MessageDigest.getInstance("SHA-256")
                    .digest(healthInfoBytes + sha256ObjectMapper.writeValueAsBytes(signerHpr))
                    .toHexString()
        }

    return Sha256Result(sha256HealthInformation, sha256WithSigner)
}
