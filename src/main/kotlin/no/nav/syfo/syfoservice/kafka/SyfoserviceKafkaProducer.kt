package no.nav.syfo.syfoservice.kafka

import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.syfoservice.kafka.model.KafkaMessageMetadata
import no.nav.syfo.sykmelding.kafka.model.SykmeldingSyfoserviceKafkaMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord

class SyfoserviceKafkaProducer(private val kafkaProducer: KafkaProducer<String, SykmeldingSyfoserviceKafkaMessage>, private val topic: String) {
    fun publishSykmeldingToKafka(sykmeldingId: String, helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet) {
        kafkaProducer.send(ProducerRecord(topic, sykmeldingId, SykmeldingSyfoserviceKafkaMessage(
                metadata = KafkaMessageMetadata(sykmeldingId),
                helseopplysninger = helseOpplysningerArbeidsuforhet
        )))
    }
}
