package no.nav.syfo

import no.nav.syfo.util.erTestFnr
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class TestFnrTest {

    @Test
    internal fun `Skal treffe paa fnr`() {
        Assertions.assertEquals(true, erTestFnr("14077700162"))
    }

    @Test
    internal fun `Skal ikkje treffe paa fnr`() {
        Assertions.assertEquals(false, erTestFnr("14077700161"))
    }
}
