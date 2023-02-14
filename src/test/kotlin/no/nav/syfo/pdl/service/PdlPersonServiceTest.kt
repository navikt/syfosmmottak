package no.nav.syfo.pdl.service

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
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PdlPersonServiceTest {
    val pdlClient = mockkClass(PdlClient::class)
    val accessTokenClientV2 = mockkClass(AccessTokenClientV2::class)
    val pdlPersonService = PdlPersonService(pdlClient, accessTokenClientV2, "littaScope")

    val loggingMeta = LoggingMeta("mottakid", "orgnr", "msgid")

    @BeforeEach
    internal fun `Set up`() {
        clearAllMocks()
        coEvery { accessTokenClientV2.getAccessTokenV2(any()) } returns "token"
    }

    @Test
    internal fun `Henter aktorid for pasient og lege`() {
        coEvery { pdlClient.getIdenter(any(), any()) } returns GetPersonResponse(
            ResponseData(
                hentIdenterBolk = listOf(
                    HentIdenterBolk(
                        "fnrPasient",
                        listOf(
                            PdlIdent("aktorIdPasient", false, "AKTORID"),
                            PdlIdent("fnrPasient", false, "FOLKEREGISTERIDENT")
                        ),
                        "ok"
                    ),
                    HentIdenterBolk(
                        "fnrLege",
                        listOf(
                            PdlIdent("aktorIdLege", false, "AKTORID"),
                            PdlIdent("fnrLege", false, "FOLKEREGISTERIDENT")
                        ),
                        "ok"
                    )
                )
            ),
            errors = null
        )

        runBlocking {
            val identer = pdlPersonService.getIdenter(listOf("fnrPasient", "fnrLege"), loggingMeta)

            Assertions.assertEquals("aktorIdPasient", identer["fnrPasient"]?.aktorId)
            Assertions.assertEquals("aktorIdLege", identer["fnrLege"]?.aktorId)
        }
    }

    @Test
    internal fun `Henter nyeste fnr som fnr for pasient med flere identer`() {
        coEvery { pdlClient.getIdenter(any(), any()) } returns GetPersonResponse(
            ResponseData(
                hentIdenterBolk = listOf(
                    HentIdenterBolk(
                        "fnrPasient",
                        listOf(
                            PdlIdent("aktorIdPasient", false, "AKTORID"),
                            PdlIdent("gammeltFnrPasient", true, "FOLKEREGISTERIDENT"),
                            PdlIdent("fnrPasient", false, "FOLKEREGISTERIDENT")
                        ),
                        "ok"
                    ),
                    HentIdenterBolk(
                        "fnrLege",
                        listOf(
                            PdlIdent("aktorIdLege", false, "AKTORID"),
                            PdlIdent("fnrLege", false, "FOLKEREGISTERIDENT")
                        ),
                        "ok"
                    )
                )
            ),
            errors = null
        )

        runBlocking {
            val identer = pdlPersonService.getIdenter(listOf("fnrPasient", "fnrLege"), loggingMeta)

            Assertions.assertEquals("aktorIdPasient", identer["fnrPasient"]?.aktorId)
            Assertions.assertEquals("aktorIdLege", identer["fnrLege"]?.aktorId)
        }
    }

    @Test
    internal fun `Pasient-aktorid er null hvis pasient ikke finnes i PDL`() {
        coEvery { pdlClient.getIdenter(any(), any()) } returns GetPersonResponse(
            ResponseData(
                hentIdenterBolk = listOf(
                    HentIdenterBolk("fnrPasient", null, "not_found"),
                    HentIdenterBolk(
                        "fnrLege",
                        listOf(
                            PdlIdent("aktorIdLege", false, "AKTORID"),
                            PdlIdent("fnrLege", false, "FOLKEREGISTERIDENT")
                        ),
                        "ok"
                    )
                )
            ),
            errors = null
        )
        runBlocking {
            val identer = pdlPersonService.getIdenter(listOf("fnrPasient", "fnrLege"), loggingMeta)

            Assertions.assertEquals(null, identer["fnrPasient"]?.aktorId)
            Assertions.assertEquals("aktorIdLege", identer["fnrLege"]?.aktorId)
        }
    }

    @Test
    internal fun `Skal feile naar ingen identer finnes`() {
        coEvery {
            pdlClient.getIdenter(
                any(),
                any()
            )
        } returns GetPersonResponse(ResponseData(hentIdenterBolk = emptyList()), errors = null)

        assertThrows<IllegalStateException> {
            runBlocking {
                pdlPersonService.getIdenter(listOf("fnrPasient", "fnrLege"), loggingMeta)
            }
        }
    }
}
