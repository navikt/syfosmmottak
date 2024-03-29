package no.nav.syfo.service

import java.util.Base64
import no.nav.syfo.client.ClamAvClient
import no.nav.syfo.client.Status
import no.nav.syfo.logger
import no.nav.syfo.metrics.VEDLEGG_OVER_300_MEGABYTE_COUNTER
import no.nav.syfo.vedlegg.model.Vedlegg

class VirusScanService(
    private val clamAvClient: ClamAvClient,
) {

    suspend fun vedleggContainsVirus(vedlegg: List<Vedlegg>): Boolean {
        val vedleggOver300MegaByte =
            vedlegg.filter {
                fileSizeLagerThan300MegaBytes(Base64.getMimeDecoder().decode(it.content.content))
            }

        if (vedleggOver300MegaByte.isNotEmpty()) {
            logVedleggOver300MegaByteMetric(vedleggOver300MegaByte)
        }

        val vedleggUnder300MegaByte =
            vedlegg.filter {
                !fileSizeLagerThan300MegaBytes(Base64.getMimeDecoder().decode(it.content.content))
            }

        return if (vedleggUnder300MegaByte.isEmpty()) {
            false
        } else {
            logger.info(
                "Scanning vedlegg for virus, numbers of vedlegg: " + vedleggUnder300MegaByte.size
            )
            val scanResultMayContainVirus =
                clamAvClient.virusScanVedlegg(vedleggUnder300MegaByte).filter {
                    it.Result != Status.OK
                }
            scanResultMayContainVirus.map {
                logger.warn("Vedlegg may contain virus, filename: " + it.Filename)
            }
            scanResultMayContainVirus.isNotEmpty()
        }
    }
}

fun logVedleggOver300MegaByteMetric(vedlegg: List<Vedlegg>) {
    vedlegg.forEach {
        logger.info("Vedlegg is over 300 megabyte: " + it.description)
        VEDLEGG_OVER_300_MEGABYTE_COUNTER.inc()
    }
}

fun fileSizeLagerThan300MegaBytes(file: ByteArray): Boolean {
    return (file.size / 1024) / 1024 > 300
}
