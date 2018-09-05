package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val srvSyfoSmMottakUsername: String = getEnvVar("SRVSYFOSMMOTTAK_USERNAME"),
    val srvSyfoSMMottakPassword: String = getEnvVar("SRVSYFOSMMOTTAK_PASSWORD"),
    val mqHostname: String = getEnvVar("MQGATEWAY03_HOSTNAME"),
    val mqPort: Int = getEnvVar("MQGATEWAY03_PORT").toInt(),
    val mqGatewayName: String = getEnvVar("MQGATEWAY03_NAME"),
    val mqChannelName: String = getEnvVar("SYFOMOTTAK_CHANNEL_NAME"),
    val srvappserverUsername: String = getEnvVar("SRVAPPSERVER_USERNAME", "srvappserver"),
    val srvappserverPassword: String = getEnvVar("SRVAPPSERVER_PASSWORD", ""),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfomottakinputQueueName: String = getEnvVar("SYFOMOTTAK_INPUT_QUEUE_QUEUENAME"),
    val syfoSmRegelerApiURL: String = getEnvVar("SYFO_SYKEMELDINGREGLER_API_URL", "syfosmregler"),
    val apprecQueue: String = getEnvVar("MOTTAK_QUEUE_UTSENDING_QUEUENAME"),
    val redisHost: String = getEnvVar("REDIS_HOST"),
    val sm2013ManualHandlingTopic: String = getEnvVar("SM2013_MANUAL_HANDLING_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("SM2013_AUTOMATIC_HANDLING_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val syfomottakinputBackoutQueueName: String = getEnvVar("SYFOMOTTAK_BACKOUT_QUEUE_QUEUENAME")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
