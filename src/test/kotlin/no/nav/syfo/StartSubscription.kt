package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.client.KuhrSarClientSpek
import no.nav.syfo.client.Samhandler
import no.nav.syfo.client.samhandlerpraksisIsLegevakt
import org.amshove.kluent.shouldBeEqualTo

class StartSubscription : FunSpec({

    val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    context("Oppretter subscription") {
        val samhandlerleva: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_leva.json").readBytes().toString(Charsets.UTF_8))
        val samhandlerleko: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_leko.json").readBytes().toString(Charsets.UTF_8))
        val samhandlerfale: List<Samhandler> = objectMapper.readValue(KuhrSarClientSpek::class.java.getResourceAsStream("/kuhr_sahr_fale.json").readBytes().toString(Charsets.UTF_8))

        test("Skal opprette subscription") {
            !samhandlerpraksisIsLegevakt(samhandlerfale.first().samh_praksis.first()) shouldBeEqualTo true
        }

        test("Skal ikkje opprette subscription når samhanlder praksis er legevakt") {
            !samhandlerpraksisIsLegevakt(samhandlerleva.first().samh_praksis.first()) shouldBeEqualTo false
        }

        test("Skal ikkje opprette subscription når samhanlder praksis er legevakt") {
            !samhandlerpraksisIsLegevakt(samhandlerleko.first().samh_praksis.first()) shouldBeEqualTo false
        }
    }
})
