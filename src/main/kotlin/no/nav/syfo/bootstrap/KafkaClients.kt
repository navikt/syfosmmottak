package no.nav.syfo.bootstrap

import io.confluent.kafka.serializers.KafkaAvroSerializer
import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.vedlegg.producer.KafkaVedleggProducer
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig

class KafkaClients constructor(env: Environment, credentials: VaultCredentials) {

    private val kafkaBaseConfig = loadBaseConfig(env, credentials)
    private val producerProperties = kafkaBaseConfig
            .toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    private val manualValidationProducerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)
    private val vedleggProducerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    init {
        producerProperties[ProducerConfig.RETRIES_CONFIG] = 100_000
        producerProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        manualValidationProducerProperties[ProducerConfig.RETRIES_CONFIG] = 100_000
        manualValidationProducerProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        vedleggProducerProperties[ProducerConfig.RETRIES_CONFIG] = 100_000
        vedleggProducerProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        vedleggProducerProperties[ProducerConfig.MAX_REQUEST_SIZE_CONFIG] = "8388608"
    }

    val kafkaProducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)
    val kafkaProducerValidationResult = KafkaProducer<String, ValidationResult>(producerProperties)
    val kafkaProducerApprec = KafkaProducer<String, Apprec>(producerProperties)
    val manualValidationKafkaProducer = KafkaProducer<String, ProduceTask>(manualValidationProducerProperties)
    val kafkaproducerManuellOppgave = KafkaProducer<String, ManuellOppgave>(producerProperties)
    val kafkaVedleggProducer = KafkaVedleggProducer(env, KafkaProducer(vedleggProducerProperties))
}
