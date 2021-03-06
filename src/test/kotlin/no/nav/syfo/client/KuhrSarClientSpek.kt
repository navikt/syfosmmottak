package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeLessThan
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.fail

object KuhrSarClientSpek : Spek({

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    describe("KuhrSarClient") {
        val samhandler: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_response.json").readBytes().toString(Charsets.UTF_8))

        it("Finner en aktiv samhandler praksis") {
            val match = findBestSamhandlerPraksis(samhandler, "SomeInvalidName", null, LoggingMeta("", "", ""))
                ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldBeLessThan 50.0
        }

        it("Foretrekker samhandler praksisen med en matchende her id selv om navnet er likt") {
            val match = findBestSamhandlerPraksis(samhandler, "Testlegesenteret", "12345", LoggingMeta("", "", ""))
                ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldBeEqualTo 100.0
            match.samhandlerPraksis.samh_praksis_id shouldBeEqualTo "1000456788"
        }

        it("Finner en samhandler praksis når navnet matcher 100%") {
            val match = findBestSamhandlerPraksis(samhandler, "Testlegesenteret", null, LoggingMeta("", "", ""))
                ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldBeEqualTo 100.0
            match.samhandlerPraksis.samh_praksis_id shouldBeEqualTo "1000456789"
        }

        it("Finner en samhandler praksis når her iden ikke matcher") {
            val match = findBestSamhandlerPraksis(samhandler, "Testlegesenteret", "23456", LoggingMeta("", "", ""))
                ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldBeEqualTo 100.0
            match.samhandlerPraksis.samh_praksis_id shouldBeEqualTo "1000456789"
        }

        it("Finner en samhandler som har navn på praksis når noen mangler navn") {
            val samhandlerMedNavn: List<Samhandler> = objectMapper.readValue(
                KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_response_falo.json").readBytes()
                    .toString(Charsets.UTF_8)
            )
            val match =
                findBestSamhandlerPraksis(samhandlerMedNavn, "Testlegesenteret", "23456", LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.samhandlerPraksis.samh_praksis_id shouldBeEqualTo "1000456788"
        }

        it("Finner en samhandler når det bare er inaktivte samhandlere") {
            val samhandlerMedNavn: List<Samhandler> = objectMapper.readValue(
                KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_response_inaktive.json").readBytes()
                    .toString(Charsets.UTF_8)
            )

            val match = samhandlerMatchingPaaOrganisjonsNavn(samhandlerMedNavn, "Testlegesenteret")

            match?.samhandlerPraksis?.navn shouldBeEqualTo "Testlegesenteret - org nr"
        }
    }
})
