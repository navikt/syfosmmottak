package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig

data class Environment(
    val legeSuspensjonEndpointURL: String = getEnvVar("LEGE_SUSPENSJON_ENDPOINT_URL", "http://btsys.default"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api.teamkuhr.svc.nais.local"),
    val syfosmreglerApiUrl: String = getEnvVar("SYFOSMREGLER_API_URL", "http://syfosmregler"),
    val subscriptionEndpointURL: String = getEnvVar("SUBSCRIPTION_ENDPOINT_URL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL", "http://security-token-service.default/rest/v1/sts/token"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmmottak"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013InvalidHandlingTopic: String = getEnvVar("KAFKA_SM2013_INVALID_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val sm2013OppgaveTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "aapen-syfo-oppgave-produserOppgave"),
    val sm2013BehandlingsUtfallTopic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    val syfoSmManuellTopic: String = getEnvVar("KAFKA_SYFO_SM_MANUELL_TOPIC", "privat-syfo-sm2013-manuell"),
    override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
    override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val syfoserviceQueueName: String = getEnvVar("MQ_SYFOSERVICE_QUEUE_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val redisHost: String = getEnvVar("REDIS_HOST", "syfosmmottak-redis.teamsykmelding.svc.nais.local"),
    val sm2013Apprec: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-apprec-v1"),
    override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val sm2013VedleggTopic: String = "privat-syfo-vedlegg",
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val helsenettproxyScope: String = getEnvVar("HELSENETT_SCOPE")
) : MqConfig, KafkaConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val redisSecret: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
