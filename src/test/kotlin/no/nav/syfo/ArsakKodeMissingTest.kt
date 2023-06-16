package no.nav.syfo

import java.io.StringReader
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.aktivitetIkkeMuligArbeidsplassenArsakskodeMangler
import no.nav.syfo.util.aktivitetIkkeMuligMedisinskeArsakskodeMangler
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.medisinskeArsakskodeHarUgyldigVerdi
import no.nav.syfo.util.medisinskeArsakskodeMangler
import no.nav.syfo.utils.getFileAsString
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class ArsakKodeMissingTest {
    @Test
    internal fun `Validate Medisinske Arsaker Arsakskode is mapped`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(
                StringReader(getFileAsString("src/test/resources/generated_sm_8.xml"))
            ) as HelseOpplysningerArbeidsuforhet

        Assertions.assertEquals(false, medisinskeArsakskodeMangler(healthInformation))
    }

    @Test
    internal fun `Validate arsakskodeIsmissing is false`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(
                StringReader(getFileAsString("src/test/resources/generated_sm.xml"))
            ) as HelseOpplysningerArbeidsuforhet

        Assertions.assertEquals(false, medisinskeArsakskodeMangler(healthInformation))
    }

    @Test
    internal fun `Validate arsakskodeIsmissing is true`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(
                StringReader(getFileAsString("src/test/resources/generated_sm_9.xml"))
            ) as HelseOpplysningerArbeidsuforhet

        Assertions.assertEquals(true, medisinskeArsakskodeMangler(healthInformation))
    }

    @Test
    internal fun `Validate medisinskearsakskodeIsmissing is true`() {
        val aktivitetIkkeMulig =
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

        Assertions.assertEquals(
            false,
            aktivitetIkkeMuligMedisinskeArsakskodeMangler(aktivitetIkkeMulig)
        )
    }

    @Test
    internal fun `Validate arbeidsplassenarsakskodeIsmissing is true`() {
        val aktivitetIkkeMulig =
            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()

        Assertions.assertEquals(
            false,
            aktivitetIkkeMuligArbeidsplassenArsakskodeMangler(aktivitetIkkeMulig)
        )
    }

    @Test
    internal fun `medisinskeArsakskodeHarUgyldigVerdi er false hvis V=1`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(
                StringReader(getFileAsString("src/test/resources/generated_sm_8.xml"))
            ) as HelseOpplysningerArbeidsuforhet

        Assertions.assertEquals(false, medisinskeArsakskodeHarUgyldigVerdi(healthInformation))
    }

    @Test
    internal fun `medisinskeArsakskodeHarUgyldigVerdi er true hvis V=A`() {
        val healthInformation =
            fellesformatUnmarshaller.unmarshal(
                StringReader(getFileAsString("src/test/resources/generated_sm_10.xml"))
            ) as HelseOpplysningerArbeidsuforhet

        Assertions.assertEquals(true, medisinskeArsakskodeHarUgyldigVerdi(healthInformation))
    }
}
