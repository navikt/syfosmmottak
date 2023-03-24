package no.nav.syfo.vedlegg.model

data class Vedlegg(
    val content: Content,
    val type: String,
    val description: String,
)

data class VedleggMessage(
    val vedlegg: Vedlegg,
    val behandler: BehandlerInfo,
    val pasientAktorId: String,
    val msgId: String,
    val pasientFnr: String,
    val source: String = "syfosmmottak",
)

data class Content(val contentType: String, val content: String)

data class BehandlerInfo(val fornavn: String, val etternavn: String, val fnr: String?)
