package no.nav.syfo.apprec

import no.nav.syfo.api.RuleInfo

enum class ApprecError(val v: String, val dn: String, val s: String, val ruleinfo: RuleInfo) {
    INVALID_FNR_SIZE("X99", "Pasienten sitt fødselsnummer eller D-nummer er ikke 11 tegn.", "2.16.578.1.12.4.1.1.8221", RuleInfo("INVALID_FNR_SIZE")),
    DUPLICATE("54", "Duplikat! - Denne legeerklæringen meldingen er mottatt tidligere. Skal ikke sendes på nytt.",
            "2.16.578.1.12.4.1.1.8222", RuleInfo("DUPLICATE")),
}

fun findApprecError(listRuleinfo: List<RuleInfo>): List<ApprecError> = ApprecError.values().filter { listRuleinfo.contains(it.ruleinfo) }