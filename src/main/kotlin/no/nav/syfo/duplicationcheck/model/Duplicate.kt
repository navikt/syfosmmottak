package no.nav.syfo.duplicationcheck.model

import java.time.LocalDateTime

data class Duplicate(
    val id: String,
    val mottakId: String,
    val msgId: String,
    val duplicateMottakId: String,
    val duplicateMsgId: String,
    val mottattDate: LocalDateTime
)