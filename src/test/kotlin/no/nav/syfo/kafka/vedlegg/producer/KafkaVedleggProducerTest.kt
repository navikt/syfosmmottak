package no.nav.syfo.kafka.vedlegg.producer

import java.time.Duration
import java.util.Properties
import no.nav.common.KafkaEnvironment
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AlterConfigOp
import org.apache.kafka.clients.admin.ConfigEntry
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.ConfigResource
import org.apache.kafka.common.config.TopicConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class KafkaVedleggProducerTest : Spek({
    val embeddedEnvironment = KafkaEnvironment(
            autoStart = false,
            noOfBrokers = 3
    )

    val kafkaConfig = Properties()
    kafkaConfig.let {
        it[ProducerConfig.ACKS_CONFIG] = "all"
        it["bootstrap.servers"] = embeddedEnvironment.brokersURL
        it[ConsumerConfig.GROUP_ID_CONFIG] = "groupId"
        it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
        it[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        it[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
        it[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        it[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
        it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        it[ProducerConfig.MAX_REQUEST_SIZE_CONFIG] = "8388608"
    }
    val kafkaProducer = KafkaProducer<String, String>(kafkaConfig)
    val kafkaConsumer = KafkaConsumer<String, String>(kafkaConfig)

    val stringBuilder = StringBuilder()

    0.until(8_000_000).forEach {
        stringBuilder.append("A")
    }

    val content = stringBuilder.toString()

    beforeGroup {
        embeddedEnvironment.start()
    }
    afterGroup {
        embeddedEnvironment.tearDown()
    }
    describe("Should be able to send vedlegg") {
        it("Should send small vedlegg") {
            val adminClient = AdminClient.create(
                Properties().apply {
                    set(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedEnvironment.brokersURL)
                    set(ConsumerConfig.CLIENT_ID_CONFIG, "embkafka-adminclient")
                }
            )

            adminClient.createTopics(listOf(NewTopic("privat-syfo-vedlegg", 3, 2))).all().get()
            val resource = ConfigResource(ConfigResource.Type.TOPIC, "privat-syfo-vedlegg")
            // get the current topic configuration
            val updateConfig = HashMap<ConfigResource, Collection<AlterConfigOp>>()
            updateConfig[resource] = listOf(
                AlterConfigOp(ConfigEntry(TopicConfig.MAX_MESSAGE_BYTES_CONFIG, "8388608"), AlterConfigOp.OpType.SET),
                AlterConfigOp(ConfigEntry(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, "2"), AlterConfigOp.OpType.SET)

            )
            adminClient.incrementalAlterConfigs(updateConfig).all().get()
            kafkaProducer.send(ProducerRecord("privat-syfo-vedlegg", "1", content)).get()

            kafkaConsumer.subscribe(listOf("privat-syfo-vedlegg"))
            val messages = kafkaConsumer.poll(Duration.ofSeconds(30))
            messages.count() shouldBeEqualTo 1
            kafkaConsumer.close()
        }
    }
})
