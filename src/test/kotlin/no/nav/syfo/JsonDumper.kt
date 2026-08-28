package no.nav.syfo

import java.io.StringReader
import java.time.LocalDateTime
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

fun main() {
    val jsonMapper: JsonMapper = jacksonMapperBuilder().build()

    val sm =
        fellesformatUnmarshaller.unmarshal(
            StringReader(getFileAsString("src/test/resources/generated_sm.xml"))
        ) as HelseOpplysningerArbeidsuforhet
    println(
        jsonMapper.writeValueAsString(
            sm.toSykmelding(
                "detteerensykmeldingid",
                "41234123",
                "12890371",
                "123124334",
                LocalDateTime.now(),
                "1213415151",
                "1213415151",
            )
        )
    )

    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val inputMessageText =
        fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat

    val receivedSykmelding =
        ReceivedSykmelding(
            sykmelding =
                sm.toSykmelding(
                    "detteerensykmeldingid",
                    "41234123",
                    "12890371",
                    "123124334",
                    LocalDateTime.now(),
                    "1213415151",
                    "1213415151",
                ),
            personNrPasient = "1231231",
            tlfPasient = "1323423424",
            personNrLege = "123134",
            legeHprNr = "123219",
            legeHelsepersonellkategori = "LE",
            navLogId = "4d3fad98-6c40-47ec-99b6-6ca7c98aa5ad",
            msgId = "06b2b55f-c2c5-4ee0-8e0a-6e252ec2a550",
            legekontorOrgNr = "444333",
            legekontorOrgName = "Helese sentar",
            legekontorHerId = "33",
            legekontorReshId = "1313",
            mottattDato = LocalDateTime.now(),
            rulesetVersion = "2",
            fellesformat = jsonMapper.writeValueAsString(inputMessageText),
            tssid = "13415",
            merknader = null,
            partnerreferanse = "16524",
            vedlegg = null,
            utenlandskSykmelding = null,
        )

    println(jsonMapper.writeValueAsString(receivedSykmelding))
}
