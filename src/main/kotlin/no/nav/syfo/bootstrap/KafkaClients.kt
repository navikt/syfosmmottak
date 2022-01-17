package no.nav.syfo.bootstrap

import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.kafka.vedlegg.producer.KafkaVedleggProducer
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig

class KafkaClients constructor(env: Environment, credentials: VaultCredentials) {

    private val kafkaBaseConfig = loadBaseConfig(env, credentials)
    private val vedleggProducerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    private val producerPropertiesAiven = KafkaUtils.getAivenKafkaConfig()
        .toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)
    init {
        vedleggProducerProperties[ProducerConfig.RETRIES_CONFIG] = 100_000
        vedleggProducerProperties[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
        vedleggProducerProperties[ProducerConfig.MAX_REQUEST_SIZE_CONFIG] = "8388608"
        producerPropertiesAiven[ProducerConfig.RETRIES_CONFIG] = 100_000
        producerPropertiesAiven[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
    }

    val kafkaProducerReceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerPropertiesAiven)
    val kafkaProducerValidationResult = KafkaProducer<String, ValidationResult>(producerPropertiesAiven)
    val kafkaProducerApprec = KafkaProducer<String, Apprec>(producerPropertiesAiven)
    val manualValidationKafkaProducer = KafkaProducer<String, OpprettOppgaveKafkaMessage>(producerPropertiesAiven)
    val kafkaproducerManuellOppgave = KafkaProducer<String, ManuellOppgave>(producerPropertiesAiven)
    val kafkaVedleggProducer = KafkaVedleggProducer(env, KafkaProducer(vedleggProducerProperties))
}
