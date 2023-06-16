package no.nav.syfo.vedlegg.google

import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import java.util.UUID
import net.logstash.logback.argument.StructuredArguments
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.vedlegg.model.BehandlerInfo
import no.nav.syfo.vedlegg.model.Vedlegg
import no.nav.syfo.vedlegg.model.VedleggMessage

class BucketUploadService(
    private val bucketName: String,
    private val storage: Storage,
) {
    fun lastOppVedlegg(
        vedlegg: List<Vedlegg>,
        msgId: String,
        personNrPasient: String,
        behandlerInfo: BehandlerInfo,
        pasientAktoerId: String,
        sykmeldingId: String,
        loggingMeta: LoggingMeta,
    ): List<String> {
        log.info("Laster opp ${vedlegg.size} vedlegg", StructuredArguments.fields(loggingMeta))
        return vedlegg
            .map {
                toVedleggMessage(
                    vedlegg = it,
                    msgId = msgId,
                    personNrPasient = personNrPasient,
                    behandlerInfo = behandlerInfo,
                    pasientAktoerId = pasientAktoerId,
                )
            }
            .map { create(sykmeldingId, it, loggingMeta) }
    }

    private fun create(
        sykmeldingId: String,
        vedleggMessage: VedleggMessage,
        loggingMeta: LoggingMeta
    ): String {
        val vedleggId = "$sykmeldingId/${UUID.randomUUID()}"
        storage.create(
            BlobInfo.newBuilder(bucketName, vedleggId).build(),
            objectMapper.writeValueAsBytes(vedleggMessage)
        )
        log.info("Lastet opp vedlegg med id $vedleggId {}", StructuredArguments.fields(loggingMeta))
        return vedleggId
    }

    private fun toVedleggMessage(
        vedlegg: Vedlegg,
        msgId: String,
        personNrPasient: String,
        behandlerInfo: BehandlerInfo,
        pasientAktoerId: String,
    ): VedleggMessage {
        return VedleggMessage(
            vedlegg = vedlegg,
            msgId = msgId,
            pasientFnr = personNrPasient,
            behandler = behandlerInfo,
            pasientAktorId = pasientAktoerId,
        )
    }
}
