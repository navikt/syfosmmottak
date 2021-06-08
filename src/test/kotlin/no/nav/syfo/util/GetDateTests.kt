package no.nav.syfo.util

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDateTime

class GetDateTests : Spek({
    describe("Testing conversion between datetime formats") {
        it("Convert without offset") {
            val it = "2021-02-02T12:00:00"
            val localDateTime = getLocalDateTime(it)
            localDateTime shouldBeEqualTo LocalDateTime.parse("2021-02-02T11:00:00")
        }
        it("Convert with offset +01:00") {
            val it = "2021-02-02T12:00:00+01:00"
            val localDateTime = getLocalDateTime(it)
            localDateTime shouldBeEqualTo LocalDateTime.parse("2021-02-02T11:00:00")
        }
        it("Convert with offset +02:00") {
            val it = "2021-02-02T12:00:00+02:00"
            val localDateTime = getLocalDateTime(it)
            localDateTime shouldBeEqualTo LocalDateTime.parse("2021-02-02T10:00:00")
        }
        it("Convert with Z offset") {
            val it = "2021-02-02T12:00:00Z"
            val localDateTime = getLocalDateTime(it)
            localDateTime shouldBeEqualTo LocalDateTime.parse("2021-02-02T12:00:00")
        }
    }
})
