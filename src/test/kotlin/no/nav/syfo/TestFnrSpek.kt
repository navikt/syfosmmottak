package no.nav.syfo

import io.kotest.core.spec.style.FunSpec
import no.nav.syfo.util.erTestFnr
import org.amshove.kluent.shouldBeEqualTo

class TestFnrSpek : FunSpec({

    context("Tester at vi finner test fnr på pasient") {

        test("Skal treffe på fnr") {
            erTestFnr("14077700162") shouldBeEqualTo true
        }

        test("Skal ikkje treffe på fnr") {
            erTestFnr("14077700161") shouldBeEqualTo false
        }
    }
})
