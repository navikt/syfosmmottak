package no.nav.syfo

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val srvappnameUsername: String = getEnvVar("SRVAPPNAME_USERNAME"),
    val srvappnamePassword: String = getEnvVar("SRVAPPNAME_PASSWORD"),
    val mqQueueManagerName: String = getEnvVar("MQGATEWAY04_NAME"),
    val mqHostname: String = getEnvVar("MQGATEWAY04_HOSTNAME"),
    val mqPort: Int = getEnvVar("MQGATEWAY04_PORT").toInt(),
    val mqGatewayName: String = getEnvVar("MQGATEWAY04_NAME"),
    val mqChannelName: String = getEnvVar("#APP_NAME#"),
    val srvappserverUsername: String = getEnvVar("SRVAPPSERVER_USERNAME", "srvappserver"),
    val srvappserverPassword: String = getEnvVar("SRVAPPSERVER_PASSWORD", ""),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val syfomottakinputQueueName: String = getEnvVar("SYFOMOTTAK_INPUT_QUEUENAME")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
