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
import no.nav.syfo.logger

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

    fun getDuplicationCheck(sha256HealthInformation: String, mottakId: String): DuplicateCheck? {
        logger.info("DUP CHECK TRACE: Before DB1 $mottakId")
        val duplicationCheckSha256HealthInformation = database.extractDuplicateCheckBySha256HealthInformation(sha256HealthInformation)
        logger.info("DUP CHECK TRACE: After DB1  $mottakId")

        if (duplicationCheckSha256HealthInformation != null) {
            logger.info("DUP CHECK TRACE: was not null, returning $mottakId")
            return duplicationCheckSha256HealthInformation
        } else {
            logger.info("DUP CHECK TRACE: NULL, before get latest (DB2): $mottakId")
            val duplicationCheckMottakId =
                getLatestDuplicationCheck(
                    database.extractDuplicateCheckByMottakId(mottakId),
                )
            logger.info("DUP CHECK TRACE: NULL, after get latest (DB2): $mottakId")
            if (duplicationCheckMottakId != null) {
                logger.info("DUP CHECK TRACE: NULL, duplicationCheckMottakId: $mottakId")
                return duplicationCheckMottakId
            }
            logger.info("DUP CHECK TRACE: NULL, duplicationCheckMottakId was null: $mottakId")
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
        .digest(sha256ObjectMapper.writeValueAsBytes(helseOpplysningerArbeidsuforhet))
        .fold("") { str, it -> str + "%02x".format(it) }
