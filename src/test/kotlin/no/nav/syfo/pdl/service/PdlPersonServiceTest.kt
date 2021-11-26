package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentIdenterBolk
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.client.model.ResponseData
import no.nav.syfo.util.LoggingMeta
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import kotlin.test.assertFailsWith

@KtorExperimentalAPI
object PdlPersonServiceTest : Spek({
    val pdlClient = mockkClass(PdlClient::class)
    val accessTokenClientV2 = mockkClass(AccessTokenClientV2::class)
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClientV2, "littaScope")

    val loggingMeta = LoggingMeta("mottakid", "orgnr", "msgid")

    beforeEachTest {
        clearAllMocks()
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
    }

    describe("Test av PdlPersonService") {
        it("Henter aktørid for pasient og lege") {
            coEvery { pdlClient.getIdenter(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentIdenterBolk = listOf(
                        HentIdenterBolk("fnrPasient", listOf(PdlIdent("aktorIdPasient", false, "AKTORID"), PdlIdent("fnrPasient", false, "FOLKEREGISTERIDENT")), "ok"),
                        HentIdenterBolk("fnrLege", listOf(PdlIdent("aktorIdLege", false, "AKTORID"), PdlIdent("fnrLege", false, "FOLKEREGISTERIDENT")), "ok")
                    )
                ),
                errors = null
            )

            runBlocking {
                val identer = pdlPersonService.getIdenter(listOf("fnrPasient", "fnrLege"), loggingMeta)

                identer["fnrPasient"]?.aktorId shouldBeEqualTo "aktorIdPasient"
                identer["fnrLege"]?.aktorId shouldBeEqualTo "aktorIdLege"
            }
        }
        it("Henter nyeste fnr som fnr for pasient med flere identer") {
            coEvery { pdlClient.getIdenter(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentIdenterBolk = listOf(
                        HentIdenterBolk("fnrPasient", listOf(
                            PdlIdent("aktorIdPasient", false, "AKTORID"),
                            PdlIdent("gammeltFnrPasient", true, "FOLKEREGISTERIDENT"),
                            PdlIdent("fnrPasient", false, "FOLKEREGISTERIDENT")
                        ), "ok"),
                        HentIdenterBolk("fnrLege", listOf(PdlIdent("aktorIdLege", false, "AKTORID"), PdlIdent("fnrLege", false, "FOLKEREGISTERIDENT")), "ok")
                    )
                ),
                errors = null
            )

            runBlocking {
                val identer = pdlPersonService.getIdenter(listOf("fnrPasient", "fnrLege"), loggingMeta)

                identer["fnrPasient"]?.aktorId shouldBeEqualTo "aktorIdPasient"
                identer["fnrLege"]?.aktorId shouldBeEqualTo "aktorIdLege"
            }
        }
        it("Pasient-aktørid er null hvis pasient ikke finnes i PDL") {
            coEvery { pdlClient.getIdenter(any(), any()) } returns GetPersonResponse(
                ResponseData(
                    hentIdenterBolk = listOf(
                        HentIdenterBolk("fnrPasient", null, "not_found"),
                        HentIdenterBolk("fnrLege", listOf(PdlIdent("aktorIdLege", false, "AKTORID"), PdlIdent("fnrLege", false, "FOLKEREGISTERIDENT")), "ok")
                    )
                ),
                errors = null
            )

            runBlocking {
                val identer = pdlPersonService.getIdenter(listOf("fnrPasient", "fnrLege"), loggingMeta)

                identer["fnrPasient"]?.aktorId shouldBeEqualTo null
                identer["fnrLege"]?.aktorId shouldBeEqualTo "aktorIdLege"
            }
        }
        it("Skal feile når ingen identer finnes") {
            coEvery { pdlClient.getIdenter(any(), any()) } returns GetPersonResponse(ResponseData(hentIdenterBolk = emptyList()), errors = null)

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    pdlPersonService.getIdenter(listOf("fnrPasient", "fnrLege"), loggingMeta)
                }
            }
        }
    }
})
