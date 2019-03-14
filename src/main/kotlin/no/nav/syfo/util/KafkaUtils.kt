package no.nav.syfo.util

import no.nav.syfo.ApplicationConfig
import no.nav.syfo.VaultCredentials
import org.apache.kafka.common.serialization.Serializer
import org.apache.kafka.common.serialization.StringSerializer
import java.util.Properties
import kotlin.reflect.KClass

fun readProducerConfig(
    config: ApplicationConfig,
    credentials: VaultCredentials,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = StringSerializer::class
) = Properties().apply {
    load(ApplicationConfig::class.java.getResourceAsStream("/kafka_producer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${credentials.serviceuserUsername}\" password=\"${credentials.serviceuserPassword}\";"
    this["key.serializer"] = keySerializer.qualifiedName
    this["value.serializer"] = valueSerializer.qualifiedName
    this["bootstrap.servers"] = config.kafkaBootstrapServers
}

fun readManualTaskProducerConfig(
    config: ApplicationConfig,
    credentials: VaultCredentials,
    valueSerializer: KClass<out Serializer<out Any>>,
    keySerializer: KClass<out Serializer<out Any>> = StringSerializer::class
) = Properties().apply {
    load(ApplicationConfig::class.java.getResourceAsStream("/kafka_manualtaskproducer.properties"))
    this["sasl.jaas.config"] = "org.apache.kafka.common.security.plain.PlainLoginModule required " +
            "username=\"${credentials.serviceuserUsername}\" password=\"${credentials.serviceuserPassword}\";"
    this["key.serializer"] = keySerializer.qualifiedName
    this["value.serializer"] = valueSerializer.qualifiedName
    this["bootstrap.servers"] = config.kafkaBootstrapServers
}
