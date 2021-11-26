package no.nav.syfo.pdl.client.model

data class GetPersonResponse(
    val data: ResponseData,
    val errors: List<ResponseError>?
)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?
)

data class ResponseData(
    val hentIdenterBolk: List<HentIdenterBolk>?
)

data class HentIdenterBolk(
    val ident: String,
    val identer: List<PdlIdent>?,
    val code: String
)

data class PdlIdent(
    val ident: String,
    val historisk: Boolean,
    val gruppe: String
)

data class ErrorLocation(
    val line: String?,
    val column: String?
)

data class ErrorExtension(
    val code: String?,
    val classification: String?
)
