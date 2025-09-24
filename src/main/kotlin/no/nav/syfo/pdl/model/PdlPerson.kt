package no.nav.syfo.pdl.model

import no.nav.syfo.pdl.client.model.PdlIdent

data class PdlPerson(
    val identer: List<PdlIdent>,
) {
    val fnr: String? =
        identer.firstOrNull { it.gruppe == "FOLKEREGISTERIDENT" && !it.historisk }?.ident
    val aktorId: String? = identer.firstOrNull { it.gruppe == "AKTORID" && !it.historisk }?.ident
    val folkereigsterIdenter: List<String> =
        identer.filter { it.gruppe == "FOLKEREGISTERIDENT" }.sortedBy { it.historisk }.map { it.ident }
}
