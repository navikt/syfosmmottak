package no.nav.syfo.service

import com.ctc.wstx.exc.WstxException
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import java.io.IOException

suspend fun fetchDiskresjonsKode(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): String? =
        retry(callName = "tps_hent_person",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            personV3.hentPerson(HentPersonRequest()
                    .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(receivedSykmelding.personNrPasient)))
            ).person?.diskresjonskode?.value
        }