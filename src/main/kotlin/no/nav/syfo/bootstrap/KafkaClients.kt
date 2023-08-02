package no.nav.syfo.bootstrap

import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.kafka.aiven.KafkaUtils
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.JacksonKafkaSerializer
import org.apache.kafka.clients.producer.KafkaProducer

class KafkaClients(environmentVariables: EnvironmentVariables) {

    val kafkaProducerReceivedSykmelding =
        KafkaProducer<String, ReceivedSykmelding>(
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
