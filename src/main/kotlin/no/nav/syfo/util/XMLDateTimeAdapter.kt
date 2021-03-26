package no.nav.syfo.util

import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.GregorianCalendar
import javax.xml.bind.DatatypeConverter
import no.nav.syfo.log

class XMLDateTimeAdapter : LocalDateTimeXmlAdapter() {
    override fun unmarshal(stringValue: String?): LocalDateTime? = when (stringValue) {
        null -> null
        else -> (DatatypeConverter.parseDateTime(stringValue) as GregorianCalendar).toZonedDateTime().toLocalDateTime()
    }
}

class XMLDateAdapter : LocalDateXmlAdapter() {
    override fun unmarshal(stringValue: String?): LocalDate? = when (stringValue) {
        null -> null
        else -> {
            log.info("Received date $stringValue")
            DatatypeConverter.parseDate(stringValue).toInstant().atZone(ZoneOffset.MAX).toLocalDate()
        }
    }
}
