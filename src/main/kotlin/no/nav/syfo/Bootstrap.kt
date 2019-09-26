package no.nav.syfo

import com.ctc.wstx.exc.WstxException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.emottak.subscription.StartSubscriptionRequest
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLIdent
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.msgHead.XMLSender
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.toApprec
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SamhandlerPraksis
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.helpers.retry
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.AVVIST_ULIK_SENDER_OG_BEHANDLER
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.INVALID_MESSAGE_NO_NOTICE
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.sak.avro.PrioritetType
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.ws.createPort
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.binding.ArbeidsfordelingV1
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.ArbeidsfordelingKriterier
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Diskresjonskoder
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Oppgavetyper
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Tema
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeRequest
import no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.meldinger.FinnBehandlendeEnhetListeResponse
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.GeografiskTilknytning
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personidenter
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentGeografiskTilknytningResponse
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Session
import javax.jms.TextMessage
import org.apache.kafka.clients.producer.ProducerRecord
import redis.clients.jedis.Jedis
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import java.io.StringWriter
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import java.util.concurrent.Executors
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.xml.bind.Marshaller

fun doReadynessCheck(): Boolean {
    return true
}

val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

val coroutineContext = Executors.newFixedThreadPool(2).asCoroutineDispatcher()

data class ApplicationState(
    var running: Boolean = true,
    var ready: Boolean = false
)

val log = LoggerFactory.getLogger("nav.syfosmmottak-application")!!

const val NAV_OPPFOLGING_UTLAND_KONTOR_NR = "0393"

@KtorExperimentalAPI
fun main() = runBlocking(coroutineContext) {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    DefaultExports.initialize()

    connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
        connection.start()

        Jedis(env.redishost, 6379).use { jedis ->
            val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

            val inputconsumer = session.consumerForQueue(env.inputQueueName)
            val syfoserviceProducer = session.producerForQueue(env.syfoserviceQueueName)
            val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)

            val kafkaBaseConfig = loadBaseConfig(env, credentials)

            val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

            val kafkaproducerreceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)

            val kafkaproducervalidationResult = KafkaProducer<String, ValidationResult>(producerProperties)

            val kafkaproducerApprec = KafkaProducer<String, Apprec>(producerProperties)

            val manualTaskproducerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)
            val manualTaskkafkaproducer = KafkaProducer<String, ProduceTask>(manualTaskproducerProperties)

            val simpleHttpClient = HttpClient(Apache) {
                install(JsonFeature) {
                    serializer = JacksonSerializer {
                        registerKotlinModule()
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
            }

            val httpClientMedBasicAuth = HttpClient(Apache) {
                install(Auth) {
                    basic {
                        username = credentials.serviceuserUsername
                        password = credentials.serviceuserPassword
                        sendWithoutRequest = true
                    }
                }
                install(JsonFeature) {
                    serializer = JacksonSerializer {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
            }
            val syfoSykemeldingRuleClient = SyfoSykemeldingRuleClient(env.syfosmreglerApiUrl, httpClientMedBasicAuth)

            val sarClient = SarClient(env.kuhrSarApiUrl, httpClientMedBasicAuth)

            val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
            val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient, simpleHttpClient)

            val subscriptionEmottak = createPort<SubscriptionPort>(env.subscriptionEndpointURL) {
                proxy { features.add(WSAddressingFeature()) }
                port { withBasicAuth(credentials.serviceuserUsername, credentials.serviceuserPassword) }
            }

            val arbeidsfordelingV1 = createPort<ArbeidsfordelingV1>(env.arbeidsfordelingV1EndpointURL) {
                port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
            }

            val personV3 = createPort<PersonV3>(env.personV3EndpointURL) {
                port { withSTS(credentials.serviceuserUsername, credentials.serviceuserPassword, env.securityTokenServiceUrl) }
            }

            launchListeners(env, applicationState, inputconsumer, syfoserviceProducer, backoutProducer,
                    subscriptionEmottak, kafkaproducerreceivedSykmelding, kafkaproducervalidationResult,
                    syfoSykemeldingRuleClient, sarClient, aktoerIdClient,
                    credentials, jedis, manualTaskkafkaproducer,
                    personV3, session, arbeidsfordelingV1, kafkaproducerApprec)

            Runtime.getRuntime().addShutdownHook(Thread {
                connection.close()
                applicationServer.stop(10, 10, TimeUnit.SECONDS)
            })
        }
    }
}

fun CoroutineScope.createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        launch {
            try {
                action()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", e.cause)
            } finally {
                applicationState.running = false
            }
        }

@KtorExperimentalAPI
suspend fun CoroutineScope.launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    inputconsumer: MessageConsumer,
    syfoserviceProducer: MessageProducer,
    backoutProducer: MessageProducer,
    subscriptionEmottak: SubscriptionPort,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    kuhrSarClient: SarClient,
    aktoerIdClient: AktoerIdClient,
    credentials: VaultCredentials,
    jedis: Jedis,
    kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>,
    personV3: PersonV3,
    session: Session,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    val listeners = (0.until(env.applicationThreads)).map {
        createListener(applicationState) {
            blockingApplicationLogic(inputconsumer, syfoserviceProducer, backoutProducer,
                    subscriptionEmottak, kafkaproducerreceivedSykmelding, kafkaproducervalidationResult,
                    syfoSykemeldingRuleClient, kuhrSarClient, aktoerIdClient, env,
                    credentials, applicationState, jedis, kafkaManuelTaskProducer,
                    personV3, session, arbeidsfordelingV1, kafkaproducerApprec)
        }
    }.toList()

    applicationState.ready = true
    listeners.forEach { it.join() }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}

@KtorExperimentalAPI
suspend fun blockingApplicationLogic(
    inputconsumer: MessageConsumer,
    syfoserviceProducer: MessageProducer,
    backoutProducer: MessageProducer,
    subscriptionEmottak: SubscriptionPort,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    kuhrSarClient: SarClient,
    aktoerIdClient: AktoerIdClient,
    env: Environment,
    credentials: VaultCredentials,
    applicationState: ApplicationState,
    jedis: Jedis,
    kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>,
    personV3: PersonV3,
    session: Session,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) = coroutineScope {
    wrapExceptions {
        loop@ while (applicationState.running) {
            val message = inputconsumer.receiveNoWait()
            if (message == null) {
                delay(100)
                continue
            }

            try {
                val inputMessageText = when (message) {
                    is TextMessage -> message.text
                    else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
                }
                INCOMING_MESSAGE_COUNTER.inc()
                val requestLatency = REQUEST_TIME.startTimer()
                val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat

                val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                val msgHead = fellesformat.get<XMLMsgHead>()

                val loggingMeta = LoggingMeta(
                        mottakId = receiverBlock.ediLoggId,
                        orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                        msgId = msgHead.msgInfo.msgId
                )

                val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                val ediLoggId = receiverBlock.ediLoggId
                val sha256String = sha256hashstring(healthInformation)
                val msgId = msgHead.msgInfo.msgId

                val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
                val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                val legekontorOrgName = msgHead.msgInfo.sender.organisation.organisationName

                val personNumberPatient = healthInformation.pasient.fodselsnummer.id
                val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur

                log.info("Received message, {}", fields(loggingMeta))
                val aktoerIds = aktoerIdClient.getAktoerIds(
                            listOf(personNumberDoctor,
                                    personNumberPatient),
                            msgId, credentials.serviceuserUsername)

                val samhandlerInfo = kuhrSarClient.getSamhandler(personNumberDoctor)
                val samhandlerPraksis = findBestSamhandlerPraksis(samhandlerInfo, legekontorOrgName, legekontorHerId,
                        loggingMeta)?.samhandlerPraksis

                when (samhandlerPraksis) {
                    null -> log.info("SamhandlerPraksis is Not found, {}", fields(loggingMeta))
                    else -> if (!samhandlerParksisisLegevakt(samhandlerPraksis)) {
                        startSubscription(subscriptionEmottak, samhandlerPraksis, msgHead, receiverBlock, loggingMeta)
                    } else {
                        log.info("SamhandlerPraksis is Legevakt, subscription_emottak is not created, {}", fields(loggingMeta))
                    }
                }

                    val redisSha256String = jedis.get(sha256String)
                    val redisEdiloggid = jedis.get(ediLoggId)

                    if (redisSha256String != null) {
                        log.warn("Message with {} marked as duplicate, has same redisSha256String {}", keyValue("originalEdiLoggId", redisSha256String), fields(loggingMeta))
                        val apprec = fellesformat.toApprec(
                                ediLoggId,
                                msgId,
                                msgHead,
                                ApprecStatus.AVVIST,
                                "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                                        "Skal ikke sendes på nytt",
                                msgHead.msgInfo.receiver.organisation,
                                msgHead.msgInfo.sender.organisation
                            )

                        sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                        log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
                        continue
                    } else if (redisEdiloggid != null) {
                        log.warn("Message with {} marked as duplicate, has same redisEdiloggid {}", keyValue("originalEdiLoggId", redisEdiloggid), fields(loggingMeta))
                        val apprec = fellesformat.toApprec(
                                ediLoggId,
                                msgId,
                                msgHead,
                                ApprecStatus.AVVIST,
                                "Duplikat! - Denne sykmeldingen er mottatt tidligere. " +
                                        "Skal ikke sendes på nytt",
                                msgHead.msgInfo.receiver.organisation,
                                msgHead.msgInfo.sender.organisation
                        )

                        sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                        log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
                        continue
                    } else {
                        val patientIdents = aktoerIds[personNumberPatient]
                        val doctorIdents = aktoerIds[personNumberDoctor]

                        if (patientIdents == null || patientIdents.feilmelding != null) {
                            log.info("Patient not found i aktorRegister error: {}, {}",
                                    keyValue("errorMessage", patientIdents?.feilmelding ?: "No response for FNR"),
                                    fields(loggingMeta))

                            val apprec = fellesformat.toApprec(
                                    ediLoggId,
                                    msgId,
                                    msgHead,
                                    ApprecStatus.AVVIST,
                                    "Pasienten er ikkje registrert i folkeregisteret",
                                    msgHead.msgInfo.receiver.organisation,
                                    msgHead.msgInfo.sender.organisation
                            )
                            sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                            log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
                            INVALID_MESSAGE_NO_NOTICE.inc()
                            updateRedis(jedis, ediLoggId, sha256String)
                            continue@loop
                        }
                        if (doctorIdents == null || doctorIdents.feilmelding != null) {
                            log.info("Doctor not found i aktorRegister error: {}, {}",
                                    keyValue("errorMessage", doctorIdents?.feilmelding ?: "No response for FNR"),
                                    fields(loggingMeta))
                            val apprec = fellesformat.toApprec(
                                    ediLoggId,
                                    msgId,
                                    msgHead,
                                    ApprecStatus.AVVIST,
                                    "Behandler er ikkje registrert i folkeregisteret",
                                    msgHead.msgInfo.receiver.organisation,
                                    msgHead.msgInfo.sender.organisation
                            )

                            sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                            log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
                            INVALID_MESSAGE_NO_NOTICE.inc()
                            updateRedis(jedis, ediLoggId, sha256String)
                            continue@loop
                        }

                        if (healthInformation.aktivitet == null || healthInformation.aktivitet.periode.isNullOrEmpty()) {
                            log.info("Periode is missing {}", fields(loggingMeta))
                            val apprec = fellesformat.toApprec(
                                    ediLoggId,
                                    msgId,
                                    msgHead,
                                    ApprecStatus.AVVIST,
                                    "Ingen perioder er oppgitt i sykmeldingen.",
                                    msgHead.msgInfo.receiver.organisation,
                                    msgHead.msgInfo.sender.organisation
                            )
                            sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                            log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
                            INVALID_MESSAGE_NO_NOTICE.inc()
                            updateRedis(jedis, ediLoggId, sha256String)
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.biDiagnoser != null && healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.v.isNullOrEmpty() }) {
                            log.info("diagnosekode is missing {}", fields(loggingMeta))
                            val apprec = fellesformat.toApprec(
                                    ediLoggId,
                                    msgId,
                                    msgHead,
                                    ApprecStatus.AVVIST,
                                    "Diagnosekode på bidiagnose mangler",
                                    msgHead.msgInfo.receiver.organisation,
                                    msgHead.msgInfo.sender.organisation
                            )
                            sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                            log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
                            INVALID_MESSAGE_NO_NOTICE.inc()
                            updateRedis(jedis, ediLoggId, sha256String)
                            continue@loop
                        }

                        if (fnrAndDnrIsmissingFromBehandler(healthInformation)) {
                            log.info("FNR or DNR is missing on behandler {}", fields(loggingMeta))
                            val apprec = fellesformat.toApprec(
                                    ediLoggId,
                                    msgId,
                                    msgHead,
                                    ApprecStatus.AVVIST,
                                    "Fødselsnummer/d-nummer på behandler mangler",
                                    msgHead.msgInfo.receiver.organisation,
                                    msgHead.msgInfo.sender.organisation
                            )
                            sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                            log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
                            INVALID_MESSAGE_NO_NOTICE.inc()
                            updateRedis(jedis, ediLoggId, sha256String)
                            continue@loop
                        }

                        val sykmelding = healthInformation.toSykmelding(
                                sykmeldingId = UUID.randomUUID().toString(),
                                pasientAktoerId = patientIdents.identer!!.first().ident,
                                legeAktoerId = doctorIdents.identer!!.first().ident,
                                msgId = msgId,
                                signaturDato = msgHead.msgInfo.genDate
                        )
                        val receivedSykmelding = ReceivedSykmelding(
                                sykmelding = sykmelding,
                                personNrPasient = personNumberPatient,
                                tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                                personNrLege = personNumberDoctor,
                                navLogId = ediLoggId,
                                msgId = msgId,
                                legekontorOrgNr = legekontorOrgNr,
                                legekontorOrgName = legekontorOrgName,
                                legekontorHerId = legekontorHerId,
                                legekontorReshId = legekontorReshId,
                                mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                                rulesetVersion = healthInformation.regelSettVersjon,
                                fellesformat = inputMessageText,
                                tssid = samhandlerPraksis?.tss_ident ?: ""
                        )

                        if (receivedSykmelding.sykmelding.behandler.fnr != personNumberDoctor) {
                            AVVIST_ULIK_SENDER_OG_BEHANDLER.inc()
                            log.warn("Behandlers fnr og avsendres fnr stemmer ikkje {}", fields(loggingMeta))
                        }

                        log.info("Validating against rules, sykmeldingId {},  {}", keyValue("sykmeldingId", sykmelding.id), fields(loggingMeta))
                        val validationResult = syfoSykemeldingRuleClient.executeRuleValidation(receivedSykmelding)

                        if (validationResult.status in arrayOf(Status.OK, Status.MANUAL_PROCESSING)) {
                            val apprec = fellesformat.toApprec(
                                    ediLoggId,
                                    msgId,
                                    msgHead,
                                    ApprecStatus.OK,
                                    null,
                                    msgHead.msgInfo.receiver.organisation,
                                    msgHead.msgInfo.sender.organisation
                            )
                            sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                            log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))

                            notifySyfoService(session, syfoserviceProducer, ediLoggId, msgId, healthInformation)
                            log.info("Message send to syfoService {}, {}", env.syfoserviceQueueName, fields(loggingMeta))
                        } else {
                            val apprec = fellesformat.toApprec(
                                    ediLoggId,
                                    msgId,
                                    msgHead,
                                    ApprecStatus.AVVIST,
                                    null,
                                    msgHead.msgInfo.receiver.organisation,
                                    msgHead.msgInfo.sender.organisation,
                                    validationResult
                            )
                            sendReceipt(apprec, env.sm2013Apprec, kafkaproducerApprec)
                            log.info("Apprec receipt sent to kafka topic {}, {}", env.sm2013Apprec, fields(loggingMeta))
                        }

                        val topicName = when (validationResult.status) {
                            Status.OK -> env.sm2013AutomaticHandlingTopic
                            Status.MANUAL_PROCESSING -> env.sm2013ManualHandlingTopic
                            Status.INVALID -> env.sm2013InvalidHandlingTopic
                        }

                        if (validationResult.status == Status.MANUAL_PROCESSING) {
                            val geografiskTilknytning = fetchGeografiskTilknytning(personV3, receivedSykmelding)
                            val patientDiskresjonsKode = fetchDiskresjonsKode(personV3, receivedSykmelding)
                            val finnBehandlendeEnhetListeResponse = fetchBehandlendeEnhet(arbeidsfordelingV1, geografiskTilknytning.geografiskTilknytning, patientDiskresjonsKode)
                            if (finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId == null) {
                                log.error("arbeidsfordeling fant ingen nav-enheter {}", fields(loggingMeta))
                            }
                            createTask(kafkaManuelTaskProducer, receivedSykmelding, validationResult, finnBehandlendeEnhetListeResponse?.behandlendeEnhetListe?.firstOrNull()?.enhetId
                                    ?: NAV_OPPFOLGING_UTLAND_KONTOR_NR, loggingMeta)
                        }

                        kafkaproducerreceivedSykmelding.send(ProducerRecord(topicName, receivedSykmelding.sykmelding.id, receivedSykmelding))
                        log.info("Message send to kafka {}, {}", topicName, fields(loggingMeta))
                        if (validationResult.status == Status.MANUAL_PROCESSING || validationResult.status == Status.INVALID) {
                            sendValidationResult(validationResult, kafkaproducervalidationResult, env, receivedSykmelding, loggingMeta)
                        }

                        val currentRequestLatency = requestLatency.observeDuration()

                        updateRedis(jedis, ediLoggId, sha256String)
                        log.info("Message got outcome {}, {}, processing took {}s",
                                keyValue("status", validationResult.status),
                                keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                                keyValue("latency", currentRequestLatency),
                                fields(loggingMeta))
                    }
            } catch (e: Exception) {
                log.error("Exception caught while handling message, sending to backout", e)
                backoutProducer.send(message)
            }
        }
    }
}

inline fun <reified T> XMLEIFellesformat.get() = this.any.find { it is T } as T

fun extractOrganisationNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
        fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
            it.typeId.v == "ENH"
        }

fun extractOrganisationRashNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
        fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
            it.typeId.v == "RSH"
        }

fun extractOrganisationHerNumberFromSender(fellesformat: XMLEIFellesformat): XMLIdent? =
        fellesformat.get<XMLMsgHead>().msgInfo.sender.organisation.ident.find {
            it.typeId.v == "HER"
        }

fun sendReceipt(
    apprec: Apprec,
    sm2013ApprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    kafkaproducerApprec.send(ProducerRecord(sm2013ApprecTopic, apprec))
}

fun Marshaller.toString(input: Any): String = StringWriter().use {
    marshal(input, it)
    it.toString()
}

fun sha256hashstring(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): String =
        MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(helseOpplysningerArbeidsuforhet))
                .fold("") { str, it -> str + "%02x".format(it) }

fun extractHelseOpplysningerArbeidsuforhet(fellesformat: XMLEIFellesformat): HelseOpplysningerArbeidsuforhet =
        fellesformat.get<XMLMsgHead>().document[0].refDoc.content.any[0] as HelseOpplysningerArbeidsuforhet

fun notifySyfoService(
    session: Session,
    receiptProducer: MessageProducer,
    ediLoggId: String,
    msgId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet

) {
    receiptProducer.send(session.createTextMessage().apply {

        val syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation)
        val sykmelding = convertSykemeldingToBase64(healthInformation)
        val syfo = Syfo(
                tilleggsdata = Tilleggsdata(ediLoggId = ediLoggId, msgId = msgId, syketilfelleStartDato = syketilfelleStartDato),
                sykmelding = Base64.getEncoder().encodeToString(sykmelding))
        text = xmlObjectWriter.writeValueAsString(syfo)
    })
}

@JacksonXmlRootElement(localName = "syfo")
data class Syfo(
    val tilleggsdata: Tilleggsdata,
    val sykmelding: String
)

data class Tilleggsdata(
    val ediLoggId: String,
    val msgId: String,
    val syketilfelleStartDato: LocalDateTime
)

fun extractSyketilfelleStartDato(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): LocalDateTime =
        LocalDateTime.of(helseOpplysningerArbeidsuforhet.syketilfelleStartDato, LocalTime.NOON)

fun convertSykemeldingToBase64(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): ByteArray =
        ByteArrayOutputStream().use {
            sykmeldingMarshaller.marshal(helseOpplysningerArbeidsuforhet, it)
            it
        }.toByteArray()

suspend fun fetchGeografiskTilknytning(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): HentGeografiskTilknytningResponse =
        retry(callName = "tps_hent_geografisktilknytning",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            personV3.hentGeografiskTilknytning(HentGeografiskTilknytningRequest().withAktoer(PersonIdent().withIdent(
                    NorskIdent()
                            .withIdent(receivedSykmelding.personNrPasient)
                            .withType(Personidenter().withValue("FNR")))))
        }

suspend fun fetchBehandlendeEnhet(arbeidsfordelingV1: ArbeidsfordelingV1, geografiskTilknytning: GeografiskTilknytning?, patientDiskresjonsKode: String?): FinnBehandlendeEnhetListeResponse? =
        retry(callName = "finn_nav_kontor",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            arbeidsfordelingV1.finnBehandlendeEnhetListe(FinnBehandlendeEnhetListeRequest().apply {
                val afk = ArbeidsfordelingKriterier()
                if (geografiskTilknytning?.geografiskTilknytning != null) {
                    afk.geografiskTilknytning = no.nav.tjeneste.virksomhet.arbeidsfordeling.v1.informasjon.Geografi().apply {
                        value = geografiskTilknytning.geografiskTilknytning
                    }
                }
                afk.tema = Tema().apply {
                    value = "SYM"
                }

                afk.oppgavetype = Oppgavetyper().apply {
                    value = "BEH_EL_SYM"
                }

                if (!patientDiskresjonsKode.isNullOrBlank()) {
                    afk.diskresjonskode = Diskresjonskoder().apply {
                        value = patientDiskresjonsKode
                    }
                }

                arbeidsfordelingKriterier = afk
            })
        }

suspend fun fetchDiskresjonsKode(personV3: PersonV3, receivedSykmelding: ReceivedSykmelding): String? =
        retry(callName = "tps_hent_person",
                retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
                legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
            personV3.hentPerson(HentPersonRequest()
                    .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(receivedSykmelding.personNrPasient)))
            ).person?.diskresjonskode?.value
        }

fun createTask(kafkaProducer: KafkaProducer<String, ProduceTask>, receivedSykmelding: ReceivedSykmelding, results: ValidationResult, navKontor: String, loggingMeta: LoggingMeta) {
    kafkaProducer.send(ProducerRecord("aapen-syfo-oppgave-produserOppgave", receivedSykmelding.sykmelding.id, ProduceTask().apply {
        messageId = receivedSykmelding.msgId
        aktoerId = receivedSykmelding.sykmelding.pasientAktoerId
        tildeltEnhetsnr = navKontor
        opprettetAvEnhetsnr = "9999"
        behandlesAvApplikasjon = "FS22" // Gosys
        orgnr = receivedSykmelding.legekontorOrgNr ?: ""
        beskrivelse = "Manuell behandling av sykmelding grunnet følgende regler: ${results.ruleHits.joinToString(", ", "(", ")") { it.messageForSender }}"
        temagruppe = "ANY"
        tema = "SYM"
        behandlingstema = "ANY"
        oppgavetype = "BEH_EL_SYM"
        behandlingstype = "ANY"
        mappeId = 1
        aktivDato = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
        fristFerdigstillelse = DateTimeFormatter.ISO_DATE.format(LocalDate.now())
        prioritet = PrioritetType.NORM
        metadata = mapOf()
    }))

    log.info("Message sendt to topic: aapen-syfo-oppgave-produserOppgave {}", fields(loggingMeta))
}

fun samhandlerParksisisLegevakt(samhandlerPraksis: SamhandlerPraksis): Boolean =
        !samhandlerPraksis.samh_praksis_type_kode.isNullOrEmpty() && (samhandlerPraksis.samh_praksis_type_kode == "LEVA" ||
                samhandlerPraksis.samh_praksis_type_kode == "LEKO")

// This functionality is only necessary due to sending out dialogMelding and oppfølginsplan to doctor
suspend fun startSubscription(
    subscriptionEmottak: SubscriptionPort,
    samhandlerPraksis: SamhandlerPraksis,
    msgHead: XMLMsgHead,
    receiverBlock: XMLMottakenhetBlokk,
    loggingMeta: LoggingMeta
) {
    log.info("SamhandlerPraksis is found, name: ${samhandlerPraksis.navn},  {}", fields(loggingMeta))
    retry(callName = "start_subscription_emottak",
            retryIntervals = arrayOf(500L, 1000L, 3000L, 5000L, 10000L),
            legalExceptions = *arrayOf(IOException::class, WstxException::class)) {
        subscriptionEmottak.startSubscription(StartSubscriptionRequest().apply {
            key = samhandlerPraksis.tss_ident
            data = convertSenderToBase64(msgHead.msgInfo.sender)
            partnerid = receiverBlock.partnerReferanse.toInt()
        })
    }
}

fun convertSenderToBase64(sender: XMLSender): ByteArray =
        ByteArrayOutputStream().use {
            senderMarshaller.marshal(sender, it)
            it
        }.toByteArray()

fun sendValidationResult(validationResult: ValidationResult, kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>, env: Environment, receivedSykmelding: ReceivedSykmelding, loggingMeta: LoggingMeta) {

    kafkaproducervalidationResult.send(ProducerRecord(env.sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult))
    log.info("Validation results send to kafka {}, {}", env.sm2013BehandlingsUtfallToipic, fields(loggingMeta))
}

fun updateRedis(jedis: Jedis, ediLoggId: String, sha256String: String) {
    jedis.setex(ediLoggId, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
}

fun fnrAndDnrIsmissingFromBehandler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.behandler.id.find { it.typeId.v == "FNR" }?.id == null && healthInformation.behandler.id.find { it.typeId.v == "DNR" }?.id == null