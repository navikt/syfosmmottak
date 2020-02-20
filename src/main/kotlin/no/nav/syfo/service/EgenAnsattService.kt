package no.nav.syfo.service

import com.ctc.wstx.exc.WstxException
import java.io.IOException
import no.nav.syfo.helpers.retry
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.pip.egen.ansatt.v1.WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest

suspend fun fetchEgenAnsatt(egenAnsattV1: EgenAnsattV1, receivedSykmelding: ReceivedSykmelding): Boolean =
        retry(callName = "tps_egen_ansatt",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            egenAnsattV1.hentErEgenAnsattEllerIFamilieMedEgenAnsatt(
                    WSHentErEgenAnsattEllerIFamilieMedEgenAnsattRequest().withIdent(receivedSykmelding.personNrPasient))
                    .isEgenAnsatt
        }
