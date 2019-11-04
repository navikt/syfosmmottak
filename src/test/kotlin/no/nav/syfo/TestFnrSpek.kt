package no.nav.syfo

import org.amshove.kluent.shouldEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TestFnrSpek : Spek({

    describe("Tester at vi finner test fnr på pasient") {

        it("Skal treffe på fnr") {
            erTestFnr("14077700162") shouldEqualTo true
        }

        it("Skal ikkje treffe på fnr") {
            erTestFnr("14077700161") shouldEqualTo false
        }
    }
})
