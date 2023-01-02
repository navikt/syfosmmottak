package no.nav.syfo.duplicationcheck.model

import java.time.LocalDateTime

data class Duplicate(
    val sykmeldingId: String,
    val mottakId: String,
    val msgId: String,
    val duplicateSykmeldingId: String,
    val mottattDate: LocalDateTime,
    val epjSystem: String,
    val epjVersion: String
)
