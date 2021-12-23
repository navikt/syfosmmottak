package no.nav.syfo

import no.nav.syfo.util.erTestFnr
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object TestFnrSpek : Spek({

    describe("Tester at vi finner test fnr på pasient") {

        it("Skal treffe på fnr") {
            erTestFnr("14077700162") shouldBeEqualTo true
        }

        it("Skal ikkje treffe på fnr") {
            erTestFnr("14077700161") shouldBeEqualTo false
        }
    }
})
