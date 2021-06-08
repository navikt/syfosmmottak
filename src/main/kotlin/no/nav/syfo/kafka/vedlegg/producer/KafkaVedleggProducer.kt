package no.nav.syfo.kafka.vedlegg.producer

import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.syfo.Environment
import no.nav.syfo.kafka.vedlegg.model.BehandlerInfo
import no.nav.syfo.kafka.vedlegg.model.Vedlegg
import no.nav.syfo.kafka.vedlegg.model.VedleggKafkaMessage
import no.nav.syfo.log
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class KafkaVedleggProducer(private val environment: Environment, private val kafkaProducer: KafkaProducer<String, VedleggKafkaMessage>) {
    fun sendVedlegg(vedlegg: List<Vedlegg>, receivedSykmelding: ReceivedSykmelding, loggingMeta: LoggingMeta) {
        vedlegg.map { toKafkaVedleggMessage(it, receivedSykmelding) }.forEach {
            try {
                log.info("Sender vedlegg til kafka, {} {}", it.vedlegg.type, loggingMeta)
                kafkaProducer.send(ProducerRecord(environment.sm2013VedleggTopic, receivedSykmelding.sykmelding.id, it)).get()
            } catch (ex: Exception) {
                log.error("Error producing vedlegg to kafka {}", fields(loggingMeta), ex)
                throw ex
            }
        }
    }

    private fun toKafkaVedleggMessage(vedlegg: Vedlegg, receivedSykmelding: ReceivedSykmelding): VedleggKafkaMessage {
        return VedleggKafkaMessage(
            vedlegg = vedlegg,
            msgId = receivedSykmelding.msgId,
            pasientFnr = receivedSykmelding.personNrPasient,
            behandler = BehandlerInfo(
                fornavn = receivedSykmelding.sykmelding.behandler.fornavn,
                etternavn = receivedSykmelding.sykmelding.behandler.etternavn,
                fnr = receivedSykmelding.personNrLege
            ),
            pasientAktorId = receivedSykmelding.sykmelding.pasientAktoerId
        )
    }
}
