package no.nav.syfo.util

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import org.amshove.kluent.shouldBeEqualTo
import java.io.StringReader

internal class ExtractHelseOpplysningerArbeisuforehetTest : FunSpec({
    context("Testing extracting data from HelseOpplysningerArbeidsuforhet") {
        test("Extract tlf from behandler") {

            val stringInput =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
            val kontaktInfo = extractHelseOpplysningerArbeidsuforhet(fellesformat).behandler.kontaktInfo

            val tlfFraBehnandler = extractTlfFromKontaktInfo(kontaktInfo)

            tlfFraBehnandler shouldBeEqualTo "12345678"
        }
        test("Extract tlf from pasient") {

            val stringInput =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
            val kontaktInfo = extractHelseOpplysningerArbeidsuforhet(fellesformat).pasient.kontaktInfo

            val tlfFraPasient = extractTlfFromKontaktInfo(kontaktInfo)

            tlfFraPasient shouldBeEqualTo "mob:12345678"
        }
        test("Extract hpr") {

            val stringInput =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
            val hpr = padHpr(extractHpr(fellesformat)?.id?.trim())

            hpr shouldBeEqualTo "123456789"
        }
    }
})
