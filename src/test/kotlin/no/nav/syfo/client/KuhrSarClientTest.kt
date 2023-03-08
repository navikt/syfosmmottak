package no.nav.syfo.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.util.LoggingMeta
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail

internal class KuhrSarClientTest {

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val samhandler: List<Samhandler> = objectMapper.readValue(
        KuhrSarClientTest::class.java.getResourceAsStream("/kuhr_sahr_response.json")!!.readBytes()
            .toString(Charsets.UTF_8)
    )

    @Test
    internal fun `Finner en aktiv samhandler praksis`() {
        val match = findBestSamhandlerPraksis(
            samhandler,
            "SomeInvalidOrgnumber",
            "SomeInvalidName",
            null,
            LoggingMeta("", "", "")
        )
            ?: fail("Unable to find samhandler praksis")
        Assertions.assertEquals(true, 50.0 > match.percentageMatch)
    }

    @Test
    internal fun `Foretrekker samhandler praksisen med en matchende her id selv om navnet er likt`() {
        val match = findBestSamhandlerPraksis(
            samhandler,
            "SomeInvalidOrgnumber",
            "Testlegesenteret",
            "12345",
            LoggingMeta("", "", "")
        )
            ?: fail("Unable to find samhandler praksis")
        Assertions.assertEquals(100.0, match.percentageMatch)
        Assertions.assertEquals("1000456788", match.samhandlerPraksis.samh_praksis_id)
    }

    @Test
    internal fun `Finner en samhandler praksis naar navnet matcher 100 prosent`() {
        val match = findBestSamhandlerPraksis(
            samhandler,
            "SomeInvalidOrgnumber",
            "Testlegesenteret",
            null,
            LoggingMeta("", "", "")
        )
            ?: fail("Unable to find samhandler praksis")
        Assertions.assertEquals(100.0, match.percentageMatch)
        Assertions.assertEquals("1000456789", match.samhandlerPraksis.samh_praksis_id)
    }

    @Test
    internal fun `Finner en samhandler praksis naar her iden ikke matcher`() {
        val match = findBestSamhandlerPraksis(
            samhandler,
            "SomeInvalidOrgnumber",
            "Testlegesenteret",
            "23456",
            LoggingMeta("", "", "")
        )
            ?: fail("Unable to find samhandler praksis")
        Assertions.assertEquals(100.0, match.percentageMatch)
        Assertions.assertEquals("1000456789", match.samhandlerPraksis.samh_praksis_id)
    }

    @Test
    internal fun `Finner en samhandler som har navn paa praksis naar noen mangler navn`() {
        val samhandlerMedNavn: List<Samhandler> = objectMapper.readValue(
            KuhrSarClientTest::class.java.getResourceAsStream("/kuhr_sahr_response_falo.json")!!.readBytes()
                .toString(Charsets.UTF_8)
        )
        val match =
            findBestSamhandlerPraksis(
                samhandlerMedNavn,
                "SomeInvalidOrgnumber",
                "Testlegesenteret",
                "23456",
                LoggingMeta("", "", "")
            )
                ?: fail("Unable to find samhandler praksis")
        Assertions.assertEquals("1000456788", match.samhandlerPraksis.samh_praksis_id)
    }

    @Test
    internal fun `Finner en samhandler naar det bare er inaktivte samhandlere`() {
        val samhandlerMedNavn: List<Samhandler> = objectMapper.readValue(
            KuhrSarClientTest::class.java.getResourceAsStream("/kuhr_sahr_response_inaktive.json")!!.readBytes()
                .toString(Charsets.UTF_8)
        )

        val match = samhandlerMatchingPaaOrganisjonsNavn(samhandlerMedNavn, "Testlegesenteret")

        Assertions.assertEquals("Testlegesenteret - org nr", match?.samhandlerPraksis?.navn)
    }

    @Test
    internal fun `Finner en samhandler praksis naar orgNummer matcher`() {
        val samhandlerWithOrg: List<Samhandler> = objectMapper.readValue(
            KuhrSarClientTest::class.java.getResourceAsStream("/kuhr_sahr_response_org.json")!!.readBytes()
                .toString(Charsets.UTF_8)
        )

        val match = findBestSamhandlerPraksisEmottak(
            samhandlerWithOrg,
            "123344",
            "23456",
            LoggingMeta("", "", ""),
            "42"
        )
            ?: fail("Unable to find samhandler praksis")
        Assertions.assertEquals(100.0, match.percentageMatch)
        Assertions.assertEquals("123344", match.samhandlerPraksis.org_id)
        Assertions.assertEquals("Testlegesenteret", match.samhandlerPraksis.navn)
    }
}
