package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.syfo.log
import no.nav.syfo.util.LoggingMeta
import org.apache.commons.text.similarity.LevenshteinDistance
import java.util.Date
import kotlin.math.max

class SarClient(
    private val endpointUrl: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient
) {
    suspend fun getSamhandler(ident: String, msgId: String): List<Samhandler> {
        val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
        return httpClient.get("$endpointUrl/kuhrsar/sar/rest/v2/samh") {
            accept(ContentType.Application.Json)
            header("Authorization", "Bearer $accessToken")
            header("Nav-Call-Id", msgId)
            parameter("ident", ident)
        }.body()
    }
}

data class Samhandler(
    val samh_id: String,
    val navn: String,
    val samh_type_kode: String,
    val behandling_utfall_kode: String,
    val unntatt_veiledning: String,
    val godkjent_manuell_krav: String,
    val ikke_godkjent_for_refusjon: String,
    val godkjent_egenandel_refusjon: String,
    val godkjent_for_fil: String,
    val endringslogg_tidspunkt_siste: Date?,
    val samh_ident: List<Samhandlerident>,
    val samh_praksis: List<SamhandlerPraksis>
)

data class SamhandlerPraksis(
    val org_id: String?,
    val refusjon_type_kode: String?,
    val laerer: String?,
    val lege_i_spesialisering: String?,
    val tidspunkt_resync_periode: Date?,
    val tidspunkt_registrert: Date?,
    val samh_praksis_status_kode: String,
    val telefonnr: String?,
    val arbeids_kommune_nr: String?,
    val arbeids_postnr: String?,
    val arbeids_adresse_linje_1: String?,
    val arbeids_adresse_linje_2: String?,
    val arbeids_adresse_linje_3: String?,
    val arbeids_adresse_linje_4: String?,
    val arbeids_adresse_linje_5: String?,
    val her_id: String?,
    val post_adresse_linje_1: String?,
    val post_adresse_linje_2: String?,
    val post_adresse_linje_3: String?,
    val post_adresse_linje_4: String?,
    val post_adresse_linje_5: String?,
    val post_kommune_nr: String?,
    val post_postnr: String?,
    val tss_ident: String,
    val navn: String?,
    val ident: String?,
    val samh_praksis_type_kode: String?,
    val samh_id: String,
    val samh_praksis_id: String,
    val samh_praksis_periode: List<SamhandlerPeriode>
)

data class SamhandlerPeriode(
    val slettet: String,
    val gyldig_fra: Date,
    val gyldig_til: Date?,
    val samh_praksis_id: String,
    val samh_praksis_periode_id: String
)

data class Samhandlerident(
    val samh_id: String?,
    val samh_ident_id: String?,
    val ident: String?,
    val ident_type_kode: String?,
    val aktiv_ident: String?
)

data class SamhandlerPraksisMatch(val samhandlerPraksis: SamhandlerPraksis, val percentageMatch: Double)

fun calculatePercentageStringMatch(str1: String?, str2: String): Double {
    val maxDistance = max(str1?.length!!, str2.length).toDouble()
    val distance = LevenshteinDistance().apply(str2, str1).toDouble()
    return (maxDistance - distance) / maxDistance
}

fun List<SamhandlerPeriode>.formaterPerioder() = joinToString(",", "periode(", ") ") { periode ->
    "${periode.gyldig_fra} -> ${periode.gyldig_til}"
}

fun List<Samhandler>.formaterPraksis() = flatMap { it.samh_praksis }
    .joinToString(",", "praksis(", ") ") { praksis ->
        "${praksis.navn}: ${praksis.samh_praksis_status_kode} ${praksis.samh_praksis_periode.formaterPerioder()}"
    }

fun findBestSamhandlerPraksisEmottak(
    samhandlere: List<Samhandler>,
    orgNumber: String?,
    herId: String?,
    loggingMeta: LoggingMeta,
    partnerreferanse: String?
): SamhandlerPraksisMatch? {

    val aktiveSamhandlere = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }

    if (!herId.isNullOrEmpty() && aktiveSamhandlere.isNotEmpty()) {
        val samhandlerByHerId = aktiveSamhandlere.find {
            it.her_id == herId
        }
        if (samhandlerByHerId != null) {
            log.info(
                "Fant samhandler basert på herid. herid: $herId,partnerreferanse: $partnerreferanse, {}, {}",
                keyValue("praksis Informasjon", samhandlere.formaterPraksis()),
                fields(loggingMeta)
            )
            return SamhandlerPraksisMatch(samhandlerByHerId, 100.0)
        }
    }

    if (!orgNumber.isNullOrEmpty() && aktiveSamhandlere.isNotEmpty()) {
        val samhandlerByOrgNumber = aktiveSamhandlere.find {
            it.org_id == orgNumber
        }
        if (samhandlerByOrgNumber != null) {
            log.info(
                "Fant samhandler basert på orgNumber. orgNumber: $orgNumber,partnerreferanse: $partnerreferanse, {}, {}",
                keyValue("praksis Informasjon", samhandlere.formaterPraksis()),
                fields(loggingMeta)
            )
            return SamhandlerPraksisMatch(samhandlerByOrgNumber, 100.0)
        }
    }

    return null
}
fun findBestSamhandlerPraksis(
    samhandlere: List<Samhandler>,
    orgNumber: String?,
    orgName: String,
    herId: String?,
    loggingMeta: LoggingMeta
): SamhandlerPraksisMatch? {
    val aktiveSamhandlere = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }

    if (aktiveSamhandlere.isEmpty()) {
        log.info(
            "Fant ingen aktive samhandlere. {}  Meta: {}, {} ",
            keyValue("praksis Informasjon", samhandlere.formaterPraksis()),
            keyValue("antall praksiser", samhandlere.size),
            fields(loggingMeta)
        )
    }

    if (!herId.isNullOrEmpty() && aktiveSamhandlere.isNotEmpty()) {
        val samhandlerByHerId = aktiveSamhandlere.find {
            it.her_id == herId
        }
        if (samhandlerByHerId != null) {
            log.info(
                "Fant samhandler basert på herid. herid: $herId, {}, {}",
                keyValue("praksis Informasjon", samhandlere.formaterPraksis()),
                fields(loggingMeta)
            )
            return SamhandlerPraksisMatch(samhandlerByHerId, 100.0)
        }
    }

    if (!orgNumber.isNullOrEmpty() && aktiveSamhandlere.isNotEmpty()) {
        val samhandlerByOrgNumber = aktiveSamhandlere.find {
            it.org_id == orgNumber
        }
        if (samhandlerByOrgNumber != null) {
            log.info(
                "Fant samhandler basert på orgNumber. orgNumber: $orgNumber, {}, {}",
                keyValue("praksis Informasjon", samhandlere.formaterPraksis()),
                fields(loggingMeta)
            )
            return SamhandlerPraksisMatch(samhandlerByOrgNumber, 100.0)
        }
    }

    val aktiveSamhandlereMedNavn = samhandlere.flatMap { it.samh_praksis }
        .filter { praksis -> praksis.samh_praksis_status_kode == "aktiv" }
        .filter { !it.navn.isNullOrEmpty() }

    if (aktiveSamhandlereMedNavn.isEmpty() && aktiveSamhandlere.isNotEmpty()) {
        val samhandlerFALEOrFALO = aktiveSamhandlere.find {
            it.samh_praksis_type_kode == SamhandlerPraksisType.FASTLEGE.kodeVerdi ||
                it.samh_praksis_type_kode == SamhandlerPraksisType.FASTLONNET.kodeVerdi
        }
        if (samhandlerFALEOrFALO != null) {
            log.info(
                "Fant samhandler basert på samhandler praksistype kode. praksistype: ${samhandlerFALEOrFALO.samh_praksis_type_kode}, {}, {}",
                keyValue("praksis Informasjon", samhandlere.formaterPraksis()),
                fields(loggingMeta)
            )
            return SamhandlerPraksisMatch(samhandlerFALEOrFALO, 999.0)
        }
    } else if (aktiveSamhandlere.isEmpty()) {
        val inaktiveSamhandlerMatchingPaaOrganisjonsNavn = samhandlerMatchingPaaOrganisjonsNavn(samhandlere, orgName)
        if (filtererBortSamhanlderPraksiserPaaProsentMatch(
                inaktiveSamhandlerMatchingPaaOrganisjonsNavn,
                70.0,
                orgName,
                loggingMeta
            ) == null
        ) {
            if (samhandlere.firstOrNull()?.samh_praksis != null &&
                samhandlere.firstOrNull()?.samh_praksis?.firstOrNull() != null
            ) {
                val firstSamhnalderPraksis = samhandlere.firstOrNull()?.samh_praksis?.firstOrNull()
                if (firstSamhnalderPraksis != null && !firstSamhnalderPraksis.tss_ident.isEmpty()) {
                    log.info(
                        "Siste utvei med tss matching ble samhandler praksis: " +
                            "Orgnumer: ${firstSamhnalderPraksis.org_id} " +
                            "Navn: ${firstSamhnalderPraksis.navn} " +
                            "Tssid: ${firstSamhnalderPraksis.tss_ident} " +
                            "Adresselinje1: ${firstSamhnalderPraksis.arbeids_adresse_linje_1} " +
                            "Samhandler praksis type: ${firstSamhnalderPraksis.samh_praksis_type_kode} " +
                            "Samhandlers hpr nummer: ${samhandlere.firstOrNull()?.samh_ident?.find { it.ident_type_kode == "HPR" }?.ident} " +
                            "{}",
                        fields(loggingMeta)
                    )
                    return SamhandlerPraksisMatch(firstSamhnalderPraksis, 999.0)
                }
            }
        } else {
            return filtererBortSamhanlderPraksiserPaaProsentMatch(
                inaktiveSamhandlerMatchingPaaOrganisjonsNavn,
                70.0,
                orgName,
                loggingMeta
            )
        }
    }

    return aktiveSamhandlereMedNavn
        .map { samhandlerPraksis ->
            SamhandlerPraksisMatch(samhandlerPraksis, calculatePercentageStringMatch(samhandlerPraksis.navn, orgName) * 100)
        }.maxByOrNull { it.percentageMatch }
}

fun samhandlerMatchingPaaOrganisjonsNavn(samhandlere: List<Samhandler>, orgName: String): SamhandlerPraksisMatch? {
    val inaktiveSamhandlereMedNavn = samhandlere.flatMap { it.samh_praksis }
        .filter { samhandlerPraksis -> samhandlerPraksis.samh_praksis_status_kode == "inaktiv" }
        .filter { samhandlerPraksis -> !samhandlerPraksis.navn.isNullOrEmpty() }
    return if (inaktiveSamhandlereMedNavn.isNotEmpty()) {
        inaktiveSamhandlereMedNavn
            .map { samhandlerPraksis ->
                SamhandlerPraksisMatch(samhandlerPraksis, calculatePercentageStringMatch(samhandlerPraksis.navn?.lowercase(), orgName.lowercase()) * 100)
            }.maxByOrNull { it.percentageMatch }
    } else {
        null
    }
}

fun filtererBortSamhanlderPraksiserPaaProsentMatch(
    samhandlerPraksis: SamhandlerPraksisMatch?,
    prosentMatch: Double,
    orgName: String,
    loggingMeta: LoggingMeta
): SamhandlerPraksisMatch? {
    return if (samhandlerPraksis != null && samhandlerPraksis.percentageMatch >= prosentMatch) {
        log.info(
            "Beste match ble samhandler praksis: " +
                "Orgnumer: ${samhandlerPraksis.samhandlerPraksis.org_id} " +
                "Navn: ${samhandlerPraksis.samhandlerPraksis.navn} " +
                "Tssid: ${samhandlerPraksis.samhandlerPraksis.tss_ident} " +
                "Adresselinje1: ${samhandlerPraksis.samhandlerPraksis.arbeids_adresse_linje_1} " +
                "Samhandler praksis type: ${samhandlerPraksis.samhandlerPraksis.samh_praksis_type_kode} " +
                "Prosent match:${samhandlerPraksis.percentageMatch} %, basert på sykmeldingens organisjons navn: $orgName " +
                "{}",
            fields(loggingMeta)
        )
        samhandlerPraksis
    } else {
        null
    }
}
