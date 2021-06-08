package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.syfo.client.KuhrSarClientSpek
import no.nav.syfo.client.Samhandler
import no.nav.syfo.service.samhandlerParksisisLegevakt
import org.amshove.kluent.shouldEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object StartSubscription : Spek({

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    describe("Oppretter subscription") {
        val samhandlerleva: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_leva.json").readBytes().toString(Charsets.UTF_8))
        val samhandlerleko: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_leko.json").readBytes().toString(Charsets.UTF_8))
        val samhandlerfale: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_fale.json").readBytes().toString(Charsets.UTF_8))

        it("Skal opprette subscription") {
            !samhandlerParksisisLegevakt(samhandlerfale.first().samh_praksis.first()) shouldEqualTo true
        }

        it("Skal ikkje opprette subscription når samhanlder praksis er legevakt") {
            !samhandlerParksisisLegevakt(samhandlerleva.first().samh_praksis.first()) shouldEqualTo false
        }

        it("Skal ikkje opprette subscription når samhanlder praksis er legevakt") {
            !samhandlerParksisisLegevakt(samhandlerleko.first().samh_praksis.first()) shouldEqualTo false
        }
    }
})
