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

    @Test
    internal fun `Finding helsepersonell kategori for KI`() {
        val godkjenninger =
            listOf(
                Godkjenning(
                    helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "FT"),
                    autorisasjon = Kode(aktiv = true, oid = 7704, verdi = "1"),
                ),
                Godkjenning(
                    helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "KI"),
                    autorisasjon = Kode(aktiv = true, oid = 7704, verdi = "1"),
                ),
            )

        val getHelsepersonellKategori = getHelsepersonellKategori(godkjenninger)
        Assertions.assertEquals("KI", getHelsepersonellKategori)
    }

    @Test
    internal fun `Finding LE kategori also check if aktiv`() {
        val godkjenninger =
            listOf(
                Godkjenning(
                    helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "LE"),
                    autorisasjon = Kode(aktiv = true, oid = 7704, verdi = "1"),
                ),
                Godkjenning(
                    helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "KI"),
                    autorisasjon = Kode(aktiv = true, oid = 7704, verdi = "1"),
                ),
            )

        val getHelsepersonellKategori = getHelsepersonellKategori(godkjenninger)
        Assertions.assertEquals("LE", getHelsepersonellKategori)
    }

    @Test
    internal fun `Finding helsepersonell kategori also check if aktiv`() {
        val godkjenninger =
            listOf(
                Godkjenning(
                    helsepersonellkategori = Kode(aktiv = false, oid = 9060, verdi = "LE"),
                    autorisasjon = Kode(aktiv = false, oid = 7704, verdi = "1"),
                ),
                Godkjenning(
                    helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "KI"),
                    autorisasjon = Kode(aktiv = true, oid = 7704, verdi = "1"),
                ),
            )

        val getHelsepersonellKategori = getHelsepersonellKategori(godkjenninger)
        Assertions.assertEquals("KI", getHelsepersonellKategori)
    }
}
