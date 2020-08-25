package no.nav.syfo.pdl.client.model

data class GetPasientOgLegeResponse(
    val data: PasientOgBehandlerResponseData,
    val errors: List<ResponseError>?
)
data class PasientOgBehandlerResponseData(
    val pasientIdenter: Identer?,
    val legeIdenter: Identer?
)

data class Identer(val identer: List<PdlIdent>?)

data class ResponseError(
    val message: String?,
    val locations: List<ErrorLocation>?,
    val path: List<String>?,
    val extensions: ErrorExtension?
)

data class PdlIdent(val gruppe: String, val ident: String)

data class ErrorLocation(
    val line: String?,
    val column: String?
)

data class ErrorExtension(
    val code: String?,
    val classification: String?
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)
