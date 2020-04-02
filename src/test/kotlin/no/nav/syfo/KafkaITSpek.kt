package no.nav.syfo

import java.net.ServerSocket
import java.time.Duration
import java.util.Properties
import no.nav.common.KafkaEnvironment
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import no.nav.syfo.kafka.toProducerConfig
import org.amshove.kluent.shouldEqual
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KafkaITSpek : Spek({
    val topic = "aapen-test-topic"
    fun getRandomPort() = ServerSocket(0).use {
        it.localPort
    }

    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            topicNames = listOf(topic)
    )

    val credentials = VaultCredentials("", "", "", "", "")
    val config = Environment(mqHostname = "mqhost", mqPort = getRandomPort(),
            mqGatewayName = "mqGateway", kafkaBootstrapServers = embeddedEnvironment.brokersURL,
            mqChannelName = "syfomottak", aktoerregisterV1Url = "localhost-aktor", subscriptionEndpointURL = "localhost-emottak",
            inputBackoutQueueName = "inputbackqueue", inputQueueName = "inputqueue",
            syfoserviceQueueName = "syfoserviequeue", applicationPort = 1, sm2013OppgaveTopic = "oppgaveTopic", securityTokenServiceUrl = "sts", personV3EndpointURL = "personv3",
            kuhrSarApiUrl = "kuhrsarApi", syfosmreglerApiUrl = "syfosmreglerApi", sm2013ManualHandlingTopic = "sm2013ManualHandlingTopic",
            sm2013AutomaticHandlingTopic = "sm2013AutomaticHandlingTopic", sm2013InvalidHandlingTopic = "sm2013InvalidHandlingTopic",
            applicationName = "syfosmmottak", sm2013Apprec = "syfoSmApprecTopic", cluster = "dev-fss",
            arbeidsfordelingAPIUrl = "", egenAnsattURL = ""
    )

    fun Properties.overrideForTest(): Properties = apply {
        remove("security.protocol")
        remove("sasl.mechanism")
    }

    val baseConfig = loadBaseConfig(config, credentials).overrideForTest()

    val producerProperties = baseConfig
            .toProducerConfig("spek.integration", valueSerializer = StringSerializer::class)
    val producer = KafkaProducer<String, String>(producerProperties)

    val consumerProperties = baseConfig
            .toConsumerConfig("spek.integration-consumer", valueDeserializer = StringDeserializer::class)
    val consumer = KafkaConsumer<String, String>(consumerProperties)
    consumer.subscribe(listOf(topic))

    beforeGroup {
        embeddedEnvironment.start()
    }

    afterGroup {
        embeddedEnvironment.tearDown()
    }

    describe("Push a message on a topic") {
        val message = "Test message"
        it("Can read the messages from the kafka topic") {
            producer.send(ProducerRecord(topic, message))

            val messages = consumer.poll(Duration.ofMillis(5000)).toList()
            messages.size shouldEqual 1
            messages[0].value() shouldEqual message
        }
    }
})
