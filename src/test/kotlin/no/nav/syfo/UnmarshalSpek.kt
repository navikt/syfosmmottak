package no.nav.syfo

import java.io.StringReader
import java.time.LocalDate
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object UnmarshalSpek : Spek({
    describe("Testing unmarshaller") {
        it("Test unmarshal dates testsett 1") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm.xml"))) as HelseOpplysningerArbeidsuforhet
            val expectedFomDate = LocalDate.of(2018, 10, 19)
            val expectedTomDate = LocalDate.of(2018, 11, 13)

            expectedFomDate shouldEqual healthInformation.aktivitet.periode.first().periodeFOMDato
            expectedTomDate shouldEqual healthInformation.aktivitet.periode.first().periodeTOMDato
        }

        it("Test unmarshal dates testsett 2") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/helseopplysninger-UTF-8.xml"))) as HelseOpplysningerArbeidsuforhet
            val expectedFomDate = LocalDate.of(2017, 9, 1)
            val expectedTomDate = LocalDate.of(2017, 10, 27)

            expectedFomDate shouldEqual healthInformation.aktivitet.periode.first().periodeFOMDato
            expectedTomDate shouldEqual healthInformation.aktivitet.periode.first().periodeTOMDato
        }

        it("Test unmarshal dates testsett 3") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_2.xml"))) as HelseOpplysningerArbeidsuforhet
            val expectedFomDate = LocalDate.of(-44, 1, 1)
            val expectedTomDate = LocalDate.of(-44, 1, 5)

            expectedFomDate shouldEqual healthInformation.aktivitet.periode.first().periodeFOMDato
            expectedTomDate shouldEqual healthInformation.aktivitet.periode.first().periodeTOMDato
        }

        it("Test unmarshal dates testsett 4") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_3.xml"))) as HelseOpplysningerArbeidsuforhet
            val expectedFomDate = LocalDate.of(12004, 4, 12)
            val expectedTomDate = LocalDate.of(12004, 4, 20)

            expectedFomDate shouldEqual healthInformation.aktivitet.periode.first().periodeFOMDato
            expectedTomDate shouldEqual healthInformation.aktivitet.periode.first().periodeTOMDato
        }

        it("Test unmarshal dates testsett 5") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_4.xml"))) as HelseOpplysningerArbeidsuforhet
            val expectedFomDate = LocalDate.of(2004, 4, 12)
            val expectedTomDate = LocalDate.of(2004, 4, 20)

            expectedFomDate shouldEqual healthInformation.aktivitet.periode.first().periodeFOMDato
            expectedTomDate shouldEqual healthInformation.aktivitet.periode.first().periodeTOMDato
        }

        it("Test unmarshal dates testsett 6") {
            val healthInformation = fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_4.xml"))) as HelseOpplysningerArbeidsuforhet
            val expectedFomDate = LocalDate.of(2004, 4, 12)
            val expectedTomDate = LocalDate.of(2004, 4, 20)

            expectedFomDate shouldEqual healthInformation.aktivitet.periode.first().periodeFOMDato
            expectedTomDate shouldEqual healthInformation.aktivitet.periode.first().periodeTOMDato
        }
    }
})
