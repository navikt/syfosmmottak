package no.nav.syfo

import java.nio.file.Path
import java.nio.file.Paths

val vaultApplicationPropertiesPath: Path = Paths.get("/var/run/secrets/nais.io/vault/credentials.json")

data class ApplicationConfig(
    val applicationPort: Int,
    val applicationThreads: Int,
    val mqHostname: String,
    val mqPort: Int,
    val mqGatewayName: String,
    val mqChannelName: String,
    val kafkaBootstrapServers: String,
    val inputQueueName: String,
    val apprecQueueName: String,
    val inputBackoutQueueName: String,
    val aktoerregisterV1Url: String,
    val sm2013ManualHandlingTopic: String,
    val sm2013AutomaticHandlingTopic: String,
    val sm2013InvalidHandlingTopic: String,
    val syfoserviceQueueName: String,
    val subscriptionEndpointURL: String,
    val kuhrSarApiUrl: String,
    val syfosmreglerApiUrl: String,
    val arbeidsfordelingV1EndpointURL: String,
    val sm2013OppgaveTopic: String,
    val securityTokenServiceUrl: String,
    val personV3EndpointURL: String
)

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
)
