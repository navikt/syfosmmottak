package no.nav.syfo.bootstrap

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaClients constructor(env: Environment, credentials: VaultCredentials) {

    private val kafkaBaseConfig = loadBaseConfig(env, credentials)
    private val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    private val manualValidationProducerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)

    val kafkaProducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
    val kafkaProducerValidationResult = KafkaProducer<String, ValidationResult>(producerProperties)
    val kafkaProducerApprec = KafkaProducer<String, Apprec>(producerProperties)
    val manualValidationKafkaProducer = KafkaProducer<String, ProduceTask>(manualValidationProducerProperties)
    val kafkaproducerManuellOppgave = KafkaProducer<String, ManuellOppgave>(producerProperties)
}
