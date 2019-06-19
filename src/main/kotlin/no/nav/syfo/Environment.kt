package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig

data class Environment(
    val legeSuspensjonEndpointURL: String = getEnvVar("LEGE_SUSPENSJON_ENDPOINT_URL", "http://btsys"),
    val kuhrSarApiUrl: String = getEnvVar("KUHR_SAR_API_URL", "http://kuhr-sar-api"),
    val syfosmreglerApiUrl: String = getEnvVar("SYFOSMREGLER_API_URL", "http://syfosmregler"),
    val organisasjonEnhetV2EndpointURL: String = getEnvVar("ORGANISASJONENHET_V2_ENDPOINT_URL"),
    val subscriptionEndpointURL: String = getEnvVar("SUBSCRIPTION_ENDPOINT_URL"),
    val securityTokenServiceUrl: String = getEnvVar("SECURITY_TOKEN_SERVICE_URL"),
    val personV3EndpointURL: String = getEnvVar("PERSON_V3_ENDPOINT_URL"),
    val aktoerregisterV1Url: String = getEnvVar("AKTOR_REGISTER_V1_URL"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationThreads: Int = getEnvVar("APPLICATION_THREADS", "4").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmmottak"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sm2013ManualHandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
    val sm2013AutomaticHandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
    val sm2013InvalidHandlingTopic: String = getEnvVar("KAFKA_SM2013_INVALID_TOPIC", "privat-syfo-sm2013-avvistBehandling"),
    val sm2013OppgaveTopic: String = getEnvVar("KAFKA_SM2013_OPPGAVE_TOPIC", "aapen-syfo-oppgave-produserOppgave"),
    val sm2013BehandlingsUtfallToipic: String = getEnvVar("KAFKA_SM2013_BEHANDLING_TOPIC", "privat-syfo-sm2013-behandlingsUtfall"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val syfoserviceQueueName: String = getEnvVar("MQ_SYFOSERVICE_QUEUE_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val apprecQueueName: String = getEnvVar("MQ_APPREC_QUEUE_NAME"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val redishost: String = getEnvVar("REDIS_HOST", "syfosmmottak-redis.default.svc.nais.local")
) : MqConfig, KafkaConfig

data class VaultCredentials(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val mqUsername: String,
    val mqPassword: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
        System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
