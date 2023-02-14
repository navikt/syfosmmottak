package no.nav.syfo

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.utils.getFileAsString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.StringReader
import java.time.LocalDate

internal class UnmarshalTest {
    @Test
    internal fun `Test unmarshal dates testsett 1`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm.xml"))) as HelseOpplysningerArbeidsuforhet
        val expectedFomDate = LocalDate.of(2018, 10, 19)
        val expectedTomDate = LocalDate.of(2018, 11, 13)

        Assertions.assertEquals(expectedFomDate, healthInformation.aktivitet.periode.first().periodeFOMDato)
        Assertions.assertEquals(expectedTomDate, healthInformation.aktivitet.periode.first().periodeTOMDato)
    }

    @Test
    internal fun `Test unmarshal dates testsett 2`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/helseopplysninger-UTF-8.xml"))) as HelseOpplysningerArbeidsuforhet
        val expectedFomDate = LocalDate.of(2017, 9, 1)
        val expectedTomDate = LocalDate.of(2017, 10, 27)

        Assertions.assertEquals(expectedFomDate, healthInformation.aktivitet.periode.first().periodeFOMDato)
        Assertions.assertEquals(expectedTomDate, healthInformation.aktivitet.periode.first().periodeTOMDato)
    }

    @Test
    internal fun `Test unmarshal dates testsett 3`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_2.xml"))) as HelseOpplysningerArbeidsuforhet
        val expectedFomDate = LocalDate.of(-44, 1, 1)
        val expectedTomDate = LocalDate.of(-44, 1, 5)

        Assertions.assertEquals(expectedFomDate, healthInformation.aktivitet.periode.first().periodeFOMDato)
        Assertions.assertEquals(expectedTomDate, healthInformation.aktivitet.periode.first().periodeTOMDato)
    }

    @Test
    internal fun `Test unmarshal dates testsett 4`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_3.xml"))) as HelseOpplysningerArbeidsuforhet
        val expectedFomDate = LocalDate.of(12004, 4, 12)
        val expectedTomDate = LocalDate.of(12004, 4, 20)

        Assertions.assertEquals(expectedFomDate, healthInformation.aktivitet.periode.first().periodeFOMDato)
        Assertions.assertEquals(expectedTomDate, healthInformation.aktivitet.periode.first().periodeTOMDato)
    }

    @Test
    internal fun `Test unmarshal dates testsett 5`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_4.xml"))) as HelseOpplysningerArbeidsuforhet
        val expectedFomDate = LocalDate.of(2004, 4, 12)
        val expectedTomDate = LocalDate.of(2004, 4, 20)

        Assertions.assertEquals(expectedFomDate, healthInformation.aktivitet.periode.first().periodeFOMDato)
        Assertions.assertEquals(expectedTomDate, healthInformation.aktivitet.periode.first().periodeTOMDato)
    }

    @Test
    internal fun `Test unmarshal dates testsett 6`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(StringReader(getFileAsString("src/test/resources/generated_sm_4.xml"))) as HelseOpplysningerArbeidsuforhet
        val expectedFomDate = LocalDate.of(2004, 4, 12)
        val expectedTomDate = LocalDate.of(2004, 4, 20)

        Assertions.assertEquals(expectedFomDate, healthInformation.aktivitet.periode.first().periodeFOMDato)
        Assertions.assertEquals(expectedTomDate, healthInformation.aktivitet.periode.first().periodeTOMDato)
    }
}
