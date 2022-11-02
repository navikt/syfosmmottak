package no.nav.syfo

import no.nav.syfo.mq.MqConfig

data class Environment(
    val smgcpProxyUrl: String = getEnvVar("SMGCP_PROXY_URL"),
    val smgcpProxyScope: String = getEnvVar("SMGCP_PROXY_SCOPE"),
    val syfosmreglerApiUrl: String = getEnvVar("SYFOSMREGLER_API_URL", "http://syfosmregler"),
    val syfosmreglerApiScope: String = getEnvVar("SYFOSMREGLER_API_SCOPE"),
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "syfosmmottak"),
    val syfoSmManuellTopic: String = "teamsykmelding.sykmelding-manuell",
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val manuellBehandlingSykmeldingTopic: String = "teamsykmelding.manuell-behandling-sykmelding",
    val avvistSykmeldingTopic: String = "teamsykmelding.avvist-sykmelding",
    val behandlingsUtfallTopic: String = "teamsykmelding.sykmelding-behandlingsutfall",
    val apprecTopic: String = "teamsykmelding.sykmelding-apprec",
    val produserOppgaveTopic: String = "teamsykmelding.oppgave-produser-oppgave",
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val inputQueueName: String = getEnvVar("MQ_INPUT_QUEUE_NAME"),
    val inputBackoutQueueName: String = getEnvVar("MQ_INPUT_BOQ_QUEUE_NAME"),
    val redisHost: String = getEnvVar("REDIS_HOST", "syfosmmottak-redis.teamsykmelding.svc.cluster.local"),
    val redisSecret: String = getEnvVar("REDIS_PASSWORD"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val norskHelsenettEndpointURL: String = getEnvVar("HELSENETT_ENDPOINT_URL"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val helsenettproxyScope: String = getEnvVar("HELSENETT_SCOPE"),
    val sykmeldingVedleggBucketName: String = getEnvVar("SYKMELDING_VEDLEGG_BUCKET_NAME"),
    val clamAvEndpointUrl: String = getEnvVar("CLAMAV_ENDPOINT_URL", "http://clamav.clamav.svc.cluster.local")
) : MqConfig

data class VaultServiceUser(
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
