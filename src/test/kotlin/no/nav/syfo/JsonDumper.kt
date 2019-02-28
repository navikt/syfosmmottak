package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.toSykmelding
import java.io.StringReader

fun main() {

    val sm = fellesformatUnmarshaller.unmarshal(StringReader(BootstrapSpek::class.java.getResourceAsStream("/generated_sm.xml").readAllBytes().toString(Charsets.UTF_8))) as HelseOpplysningerArbeidsuforhet
    println(ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule()).apply {
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            }
            .writeValueAsString(sm.toSykmelding("detteerensykmeldingid", "41234123", "12890371")))
}