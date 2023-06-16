package no.nav.syfo.util

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

fun getLocalDateTime(dateTime: String): LocalDateTime {
    try {
        return OffsetDateTime.parse(dateTime).atZoneSameInstant(ZoneOffset.UTC).toLocalDateTime()
    } catch (ex: DateTimeParseException) {
        return LocalDateTime.parse(dateTime)
            .atZone(ZoneId.of("Europe/Oslo"))
            .withZoneSameInstant(ZoneOffset.UTC)
            .toLocalDateTime()
    }
}
