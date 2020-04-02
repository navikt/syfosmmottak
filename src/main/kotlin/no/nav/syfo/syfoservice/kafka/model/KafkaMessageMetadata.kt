package no.nav.syfo.syfoservice.kafka.model

private const val SOURCE = "syfosmmottak"

data class KafkaMessageMetadata(
    val sykmeldingId: String,
    val source: String = SOURCE
)
