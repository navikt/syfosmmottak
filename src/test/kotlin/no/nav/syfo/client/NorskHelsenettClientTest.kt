package no.nav.syfo.client

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class NorskHelsenettClientTest {

    @Test
    internal fun `Finding helsepersonell kategori for LE`() {
        val godkjenninger =
            listOf(
                Godkjenning(
                    helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "LE"),
                    autorisasjon = Kode(aktiv = true, oid = 7704, verdi = "1"),
                ),
            )

        val getHelsepersonellKategori = getHelsepersonellKategori(godkjenninger)
        Assertions.assertEquals("LE", getHelsepersonellKategori)
    }
}
