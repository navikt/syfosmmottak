package no.nav.syfo.service

import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import no.nav.syfo.util.gzip
import org.slf4j.LoggerFactory

class UploadSykmeldingService(val tsmSykmeldingBucket: String, val storage: Storage) {

    companion object {
        private val logger = LoggerFactory.getLogger(UploadSykmeldingService::class.java)
    }

    fun uploadOriginalMessage(sykmeldingId: String, originalMessage: String) {
        try {
            val sykmeldingInBucket = storage.get(tsmSykmeldingBucket, sykmeldingId)
            if (sykmeldingInBucket == null) {
                val blob =
                    BlobInfo.newBuilder(
                            BlobId.of(tsmSykmeldingBucket, "$sykmeldingId/sykmelding.xml")
                        )
                        .setContentType("application/xml")
                        .setContentEncoding("gzip")
                        .build()
                val compressedData = gzip(originalMessage)
                storage.create(blob, compressedData)
            }
        } catch (ex: Exception) {
            logger.error(
                "Error uploading xml for sykmeldingId $sykmeldingId ${ex.message} ${ex.stackTrace}",
                ex
            )
        }
    }
}
