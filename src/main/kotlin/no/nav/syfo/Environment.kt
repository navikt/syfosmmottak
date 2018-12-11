package no.nav.syfo

import java.nio.file.Files
import java.nio.file.Paths
import java.util.Properties

private val vaultApplicationPropertiesPath = Paths.get("/var/run/secrets/nais.io/vault/application.properties")

private val config = Properties().apply {
    putAll(Properties().apply {
        load(Environment::class.java.getResourceAsStream("/application.properties"))
    })
    if (Files.exists(vaultApplicationPropertiesPath)) {
        load(Files.newInputStream(vaultApplicationPropertiesPath))
    }
}
fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val mqHostname: String = getEnvVar("MQGATEWAY04_HOSTNAME", "hostname"),
    val mqPort: Int = getEnvVar("MQGATEWAY04_PORT", "1413").toInt(),
    val mqGatewayName: String = getEnvVar("MQGATEWAY04_NAME", "name"),
    val mqChannelName: String = getEnvVar("SYFOSMMOTTAK_CHANNEL_NAME", "syfosmmottak"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfosmmottakinputQueueName: String = getEnvVar("SYFOSMMOTTAK_INPUT_QUEUE_QUEUENAME", "inputQueue"),
    val syfoSmRegelerApiURL: String = getEnvVar("SYFO_SYKEMELDINGREGLER_API_URL", "syfosmregler"),
    val apprecQueue: String = getEnvVar("MOTTAK_QUEUE_UTSENDING_QUEUENAME", "appreQue"),
    val redisHost: String = getEnvVar("REDIS_HOST", "redisHost"),
    val aktoerregisterV1Url: String = getEnvVar("AKTOERREGISTER_API_V1", "https://app-q1.adeo.no/aktoerregister/api/v1"), // TODO: Make this more naiserator friendly
    val sm2013ManualHandlingTopic: String = getEnvVar("SM2013_MANUAL_HANDLING_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("SM2013_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),

    val srvSyfoSmMottakUsername: String = config.getProperty("serviceuser.username"),
    val srvSyfoSMMottakPassword: String = config.getProperty("serviceuser.password"),
    val srvappserverUsername: String = config.getProperty("mq.username"),
    val srvappserverPassword: String = config.getProperty("mq.password")
)
