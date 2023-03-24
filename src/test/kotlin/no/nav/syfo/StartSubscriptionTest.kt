package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.client.KuhrSarClientTest
import no.nav.syfo.client.Samhandler
import no.nav.syfo.client.samhandlerpraksisIsLegevakt
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class StartSubscriptionTest {

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val samhandlerleva: List<Samhandler> = objectMapper.readValue(
        KuhrSarClientTest::class.java.getResourceAsStream("/kuhr_sahr_leva.json")!!.readBytes().toString(Charsets.UTF_8),
    )
    val samhandlerleko: List<Samhandler> = objectMapper.readValue(
        KuhrSarClientTest::class.java.getResourceAsStream("/kuhr_sahr_leko.json")!!.readBytes().toString(Charsets.UTF_8),
    )
    val samhandlerfale: List<Samhandler> = objectMapper.readValue(
        KuhrSarClientTest::class.java.getResourceAsStream("/kuhr_sahr_fale.json")!!.readBytes().toString(Charsets.UTF_8),
    )

    @Test
    internal fun `Skal opprette subscription`() {
        Assertions.assertEquals(true, !samhandlerpraksisIsLegevakt(samhandlerfale.first().samh_praksis.first()))
    }

    @Test
    internal fun `Skal ikkje opprette subscription naar samhanlder praksis er legevakt`() {
        Assertions.assertEquals(false, !samhandlerpraksisIsLegevakt(samhandlerleva.first().samh_praksis.first()))
    }

    @Test
    internal fun `Skal ikkje opprette subscription naar samhanlder praksis er legevakt kommunal`() {
        Assertions.assertEquals(false, !samhandlerpraksisIsLegevakt(samhandlerleko.first().samh_praksis.first()))
    }
}
