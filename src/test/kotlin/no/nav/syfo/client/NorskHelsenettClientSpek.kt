package no.nav.syfo.client

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo

internal class NorskHelsenettClientSpek : FunSpec({


    context("NorskHelsenettClient") {

        test("Finding helsepersonell kategori for LE") {

            val godkjenninger = listOf(
                Godkjenning(
                    helsepersonellkategori = Kode(aktiv = true, oid = 9060, verdi = "LE"),
                    autorisasjon = Kode(aktiv = true, oid = 7704, verdi = "1")
                )
            )


            val getHelsepersonellKategori = getHelsepersonellKategori(godkjenninger)
            getHelsepersonellKategori shouldBeEqualTo "LE"
        }
    }


})