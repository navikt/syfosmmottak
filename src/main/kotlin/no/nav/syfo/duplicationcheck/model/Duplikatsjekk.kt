package no.nav.syfo.duplicationcheck.model

import java.time.LocalDateTime

data class Duplikatsjekk(
    val sha256HealthInformation: String,
    val mottakId: String,
    val msgId: String,
    val mottattDate: LocalDateTime,
    val epjSystem: String?,
    val epjVersion: String?,
    val orgNumber: String?
)
