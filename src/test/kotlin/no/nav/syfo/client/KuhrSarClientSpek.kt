package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.LoggingMeta
import org.amshove.kluent.shouldBeLessThan
import org.amshove.kluent.shouldEqual
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
        val samhandlerIngenAktive: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_ingen_aktive_response.json").readBytes().toString(Charsets.UTF_8))

        it("Finner en aktiv samhandler praksis") {
            val match = findBestSamhandlerPraksis(samhandler, "SomeInvalidName", null, LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldBeLessThan 50.0
        }

        it("Foretrekker samhandler praksisen med en matchende her id selv om navnet er likt") {
            val match = findBestSamhandlerPraksis(samhandler, "Testlegesenteret", "12345", LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldEqual 100.0
            match.samhandlerPraksis.samh_praksis_id shouldEqual "1000456788"
        }

        it("Finner en samhandler praksis når navnet matcher 100%") {
            val match = findBestSamhandlerPraksis(samhandler, "Testlegesenteret", null, LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldEqual 100.0
            match.samhandlerPraksis.samh_praksis_id shouldEqual "1000456789"
        }

        it("Finner en samhandler praksis når her iden ikke matcher") {
            val match = findBestSamhandlerPraksis(samhandler, "Testlegesenteret", "23456", LoggingMeta("", "", ""))
                    ?: fail("Unable to find samhandler praksis")
            match.percentageMatch shouldEqual 100.0
            match.samhandlerPraksis.samh_praksis_id shouldEqual "1000456789"
        }

        it("Returnerer ingen samhandler praksiser om det ikke er noen aktive med aktive praksis perioder") {
            val match = findBestSamhandlerPraksis(samhandlerIngenAktive, "", "12345", LoggingMeta("", "", ""))

            match shouldEqual null
        }

        it("Sjekker at vi logger korrekt om samhandlerPraksis") {

           val samhandlerPraksisMeta =  samhandlerIngenAktive.formaterPraksis()

            println(samhandlerPraksisMeta)

            samhandlerPraksisMeta shouldEqual "praksis(Testlegesenteret: aktiv periode(Thu Dec 12 13:00:00 CET 2999 -> null) ,Denneharikkedetsammenavnet: ikke_aktiv periode(Sun Dec 12 13:00:00 CET 1999 -> null) ) "

        }

    }
})