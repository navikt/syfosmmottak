package no.nav.syfo.bootstrap

import java.util.Properties
import kotlin.reflect.KClass
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer

fun Properties.toProducerConfig(
    groupId: String,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = StringSerializer::class
): Properties =
    Properties().also {
        it.putAll(this)
        it[ConsumerConfig.GROUP_ID_CONFIG] = groupId
        it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = valueSerializer.java
        it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = keySerializer.java
    }

class KafkaClients(environmentVariables: EnvironmentVariables) {

    val kafkaProducerReceivedSykmelding =
        KafkaProducer<String, ReceivedSykmeldingWithValidation>(
            getkafkaProducerConfig("received-sykmelding-producer", environmentVariables)
        )
    val kafkaProducerValidationResult =
        KafkaProducer<String, ValidationResult>(
            getkafkaProducerConfig("validation-result-producer", environmentVariables)
        )
    val kafkaProducerApprec =
        KafkaProducer<String, Apprec>(
            getkafkaProducerConfig("apprec-producer", environmentVariables)
        )
    val manualValidationKafkaProducer =
        KafkaProducer<String, OpprettOppgaveKafkaMessage>(
            getkafkaProducerConfig("manual-validation-oppgave-producer", environmentVariables)
        )
    val kafkaproducerManuellOppgave =
        KafkaProducer<String, ManuellOppgave>(
            getkafkaProducerConfig("manuell-oppgave-producer", environmentVariables)
        )
}

private fun getkafkaProducerConfig(producerId: String, env: EnvironmentVariables) =
    KafkaUtils.getAivenKafkaConfig(producerId)
        .toProducerConfig(
            env.applicationName,
            valueSerializer = JacksonKafkaSerializer::class,
        )
