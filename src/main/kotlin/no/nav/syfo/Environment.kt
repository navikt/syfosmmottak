package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val srvSyfoMottakUsername: String = getEnvVar("SRVSYFOMOTTAK_USERNAME"),
    val srvSyfoMottakPassword: String = getEnvVar("SRVSYFOMOTTAK_PASSWORD"),
    val mqHostname: String = getEnvVar("MQGATEWAY03_HOSTNAME"),
    val mqPort: Int = getEnvVar("MQGATEWAY03_PORT").toInt(),
    val mqGatewayName: String = getEnvVar("MQGATEWAY03_NAME"),
    val mqChannelName: String = getEnvVar("SYFOMOTTAK_CHANNEL_NAME"),
    val srvappserverUsername: String = getEnvVar("SRVAPPSERVER_USERNAME", "srvappserver"),
    val srvappserverPassword: String = getEnvVar("SRVAPPSERVER_PASSWORD", ""),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfomottakinputQueueName: String = getEnvVar("SYFOMOTTAK_INPUT_QUEUENAME"),
    val syfoSykemeldingRegelerApiURL: String = getEnvVar("SYFO_SYKEMELDINGREGLER_API_URL", "http://syfosykemeldingregler:8080"),
    val apprecQueue: String = getEnvVar("MOTTAK_QUEUE_UTSENDING_QUEUENAME"),
    val kafkaSM2013JournalfoeringTopic: String = getEnvVar("KAFKA_SM2013_JOURNALING_TOPIC", "privat-syfomottak-journalFoer"),
    val kafkaSM2013LagOppgaveTopic: String = getEnvVar("KAFKA_SM2013_LAGOPPGAVE_TOPIC", "privat-syfomottak-lageoppgave"),
    val syfomottakinputBackoutQueueName: String = getEnvVar("SYFOMOTTAK_BACKOUT_QUEUENAME"),
    val redisHost: String = getEnvVar("REDIS_HOST")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
