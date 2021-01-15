package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.util.LoggingMeta

@KtorExperimentalAPI
class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {

    suspend fun getAktorids(identer: List<String>, loggingMeta: LoggingMeta): Map<String, String?> {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getAktorids(identer, stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte error {}, {}", it, StructuredArguments.fields(loggingMeta))
            }
        }
        if (pdlResponse.data.hentIdenterBolk == null || pdlResponse.data.hentIdenterBolk.isNullOrEmpty()) {
            log.error("Fant ikke identer i PDL {}", StructuredArguments.fields(loggingMeta))
            throw IllegalStateException("Fant ingen identer i PDL, skal ikke kunne skje!")
        }

        pdlResponse.data.hentIdenterBolk.forEach {
            if (it.code != "ok") {
                log.warn("Mottok feilkode ${it.code} fra PDL for en eller flere identer, {}", StructuredArguments.fields(loggingMeta))
            }
        }

        return pdlResponse.data.hentIdenterBolk.map {
            it.ident to it.identer?.firstOrNull { ident -> ident.gruppe == AKTORID }?.ident
        }.toMap()
    }

    companion object {
        private const val AKTORID = "AKTORID"
    }
}
