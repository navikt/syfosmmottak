package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.model.PasientLege
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {

    suspend fun getPasientOgLege(pasientIdent: String, legeIdent: String, loggingMeta: LoggingMeta): PasientLege? {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlPasientOgBehandlerResponse = pdlClient.getPasientOgBehandler(pasientIdent, legeIdent, stsToken)

        if (pdlPasientOgBehandlerResponse.errors != null) {
            pdlPasientOgBehandlerResponse.errors.forEach {
                log.error("PDL returnerte error {}, {}", it, StructuredArguments.fields(loggingMeta))
            }
        }
        if (pdlPasientOgBehandlerResponse.data.pasientIdenter?.identer == null) {
            log.error("Fant ikke identer i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        if (pdlPasientOgBehandlerResponse.data.legeIdenter?.identer == null) {
            log.error("Fant ikke legens identer i PDL {}", StructuredArguments.fields(loggingMeta))
            return null
        }
        return PasientLege(
                pasient = PdlPerson(
                        aktorId = pdlPasientOgBehandlerResponse.data.pasientIdenter.identer.first { it.gruppe == AKTORID }.ident,
                        fnr = pdlPasientOgBehandlerResponse.data.pasientIdenter.identer.first { it.gruppe == FOLKEREGISTERIDENT }.ident
                ),
                lege = PdlPerson(
                        aktorId = pdlPasientOgBehandlerResponse.data.legeIdenter.identer.first { it.gruppe == AKTORID }.ident,
                        fnr = pdlPasientOgBehandlerResponse.data.legeIdenter.identer.first { it.gruppe == FOLKEREGISTERIDENT }.ident
                )
        )
    }

    companion object {
        private const val FOLKEREGISTERIDENT = "FOLKEREGISTERIDENT"
        private const val AKTORID = "AKTORID"
    }
}
