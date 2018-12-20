package no.nav.syfo

import java.nio.file.Path
import java.nio.file.Paths

val vaultApplicationPropertiesPath: Path = Paths.get("/var/run/secrets/nais.io/vault/credentials.json")

data class ApplicationConfig(
    val applicationPort: Int = 8080,
    val mqHostname: String,
    val mqPort: Int,
    val mqGatewayName: String,
    val mqChannelName: String,
    val kafkaBootstrapServers: String,
    val inputQueueName: String,
    val apprecQueueName: String,
    val inputBackoutQueueName: String,
    val aktoerregisterV1Url: String,
    val sm2013ManualHandlingTopic: String = "privat-syfo-sm2013-manuellBehandling",
    val sm2013AutomaticHandlingTopic: String = "privat-syfo-sm2013-automatiskBehandling",
    val syfoserviceQueueName: String,
    val subscriptionEndpointURL: String
)

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)
