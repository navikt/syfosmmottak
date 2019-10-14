package no.nav.syfo

import com.ctc.wstx.exc.WstxException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.confluent.kafka.serializers.KafkaAvroSerializer
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.auth.Auth
import io.ktor.client.features.auth.providers.basic
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.StringReader
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.ZoneOffset
import java.util.UUID
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.logstash.logback.argument.StructuredArguments.fields
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.handlestatus.handleAktivitetOrPeriodeIsMissing
import no.nav.syfo.handlestatus.handleArsakskodeIsmissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeIsMissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeVerkIsMissing
import no.nav.syfo.handlestatus.handleDoctorNotFoundInAktorRegister
import no.nav.syfo.handlestatus.handleDuplicateEdiloggid
import no.nav.syfo.handlestatus.handleDuplicateSM2013Content
import no.nav.syfo.handlestatus.handleFnrAndDnrIsmissingFromBehandler
import no.nav.syfo.handlestatus.handleHouvedDiagnoseDiagnosekodeMissing
import no.nav.syfo.handlestatus.handlePatientNotFoundInAktorRegister
import no.nav.syfo.handlestatus.handleStatusINVALID
import no.nav.syfo.handlestatus.handleStatusMANUALPROCESSING
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.helpers.retry
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toProducerConfig
import no.nav.syfo.metrics.AVVIST_ULIK_SENDER_OG_BEHANDLER
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.service.samhandlerParksisisLegevakt
import no.nav.syfo.service.startSubscription
import no.nav.syfo.service.updateRedis
import no.nav.syfo.util.JacksonKafkaSerializer
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.TrackableException
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.sykmeldingMarshaller
import no.nav.syfo.util.wrapExceptions
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
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis

val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.syfosmmottak")

const val NAV_OPPFOLGING_UTLAND_KONTOR_NR = "0393"

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val credentials = objectMapper.readValue<VaultCredentials>(Paths.get("/var/run/secrets/nais.io/vault/credentials.json").toFile())
    val applicationState = ApplicationState()
    val applicationEngine = createApplicationEngine(
            env,
            applicationState)

    val applicationServer = ApplicationServer(applicationEngine)
    applicationServer.start()

    DefaultExports.initialize()

    val kafkaBaseConfig = loadBaseConfig(env, credentials)

    val producerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = JacksonKafkaSerializer::class)

    val kafkaproducerreceivedSykmelding = KafkaProducer<String, ReceivedSykmelding>(producerProperties)

    val kafkaproducervalidationResult = KafkaProducer<String, ValidationResult>(producerProperties)

    val kafkaproducerApprec = KafkaProducer<String, Apprec>(producerProperties)

    val manuelOppgaveproducerProperties = kafkaBaseConfig.toProducerConfig(env.applicationName, valueSerializer = KafkaAvroSerializer::class)
    val manuelOppgavekafkaproducer = KafkaProducer<String, ProduceTask>(manuelOppgaveproducerProperties)

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

    launchListeners(env, applicationState,
            subscriptionEmottak, kafkaproducerreceivedSykmelding, kafkaproducervalidationResult,
            syfoSykemeldingRuleClient, sarClient, aktoerIdClient,
            credentials, manuelOppgavekafkaproducer,
            personV3, arbeidsfordelingV1, kafkaproducerApprec)

    applicationState.ready = true
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
        GlobalScope.launch {
            try {
                action()
            } catch (e: TrackableException) {
                log.error("En uhåndtert feil oppstod, applikasjonen restarter {}", e.cause)
            } finally {
                applicationState.alive = false
            }
        }

@KtorExperimentalAPI
fun launchListeners(
    env: Environment,
    applicationState: ApplicationState,
    subscriptionEmottak: SubscriptionPort,
    kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    kuhrSarClient: SarClient,
    aktoerIdClient: AktoerIdClient,
    credentials: VaultCredentials,
    kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>,
    personV3: PersonV3,
    arbeidsfordelingV1: ArbeidsfordelingV1,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    createListener(applicationState) {
        connectionFactory(env).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
            Jedis(env.redishost, 6379).use { jedis ->
                connection.start()
                val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)

                val inputconsumer = session.consumerForQueue(env.inputQueueName)
                val syfoserviceProducer = session.producerForQueue(env.syfoserviceQueueName)
                val backoutProducer = session.producerForQueue(env.inputBackoutQueueName)

                blockingApplicationLogic(inputconsumer, syfoserviceProducer, backoutProducer,
                        subscriptionEmottak, kafkaproducerreceivedSykmelding, kafkaproducervalidationResult,
                        syfoSykemeldingRuleClient, kuhrSarClient, aktoerIdClient, env,
                        credentials, applicationState, jedis, kafkaManuelTaskProducer,
                        personV3, session, arbeidsfordelingV1, kafkaproducerApprec)
            }
        }
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
) {
    wrapExceptions {
        loop@ while (applicationState.ready) {
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
                    handleDuplicateSM2013Content(redisSha256String, loggingMeta, fellesformat,
                            ediLoggId, msgId, msgHead, env, kafkaproducerApprec)
                    continue@loop
                } else if (redisEdiloggid != null) {
                    handleDuplicateEdiloggid(redisEdiloggid, loggingMeta, fellesformat,
                            ediLoggId, msgId, msgHead, env, kafkaproducerApprec)
                    continue@loop
                } else {
                    val patientIdents = aktoerIds[personNumberPatient]
                    val doctorIdents = aktoerIds[personNumberDoctor]

                    if (patientIdents == null || patientIdents.feilmelding != null) {
                        handlePatientNotFoundInAktorRegister(patientIdents, loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, redisSha256String)
                        continue@loop
                    }
                    if (doctorIdents == null || doctorIdents.feilmelding != null) {
                        handleDoctorNotFoundInAktorRegister(doctorIdents, loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, redisSha256String)
                        continue@loop
                    }

                    if (healthInformation.aktivitet == null || healthInformation.aktivitet.periode.isNullOrEmpty()) {
                        handleAktivitetOrPeriodeIsMissing(loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, redisSha256String)
                        continue@loop
                    }

                    if (healthInformation.medisinskVurdering?.biDiagnoser != null &&
                            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.v.isNullOrEmpty() }) {
                        handleBiDiagnoserDiagnosekodeIsMissing(loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, redisSha256String)
                        continue@loop
                    }

                    if (healthInformation.medisinskVurdering?.biDiagnoser != null &&
                            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.s.isNullOrEmpty() }) {
                        handleBiDiagnoserDiagnosekodeVerkIsMissing(loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, redisSha256String)
                        continue@loop
                    }

                    if (fnrAndDnrIsmissingFromBehandler(healthInformation)) {
                        handleFnrAndDnrIsmissingFromBehandler(loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, redisSha256String)
                        continue@loop
                    }

                    if (healthInformation.medisinskVurdering?.hovedDiagnose?.diagnosekode != null &&
                            healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v == null) {
                        handleHouvedDiagnoseDiagnosekodeMissing(loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, redisSha256String)
                        continue@loop
                    }

                    if (arsakskodeIsmissing(healthInformation)) {
                        handleArsakskodeIsmissing(loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, redisSha256String)
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

                    when (validationResult.status) {
                        Status.OK -> handleStatusOK(
                                fellesformat,
                                ediLoggId,
                                msgId,
                                msgHead,
                                env.sm2013Apprec,
                                kafkaproducerApprec,
                                loggingMeta,
                                session,
                                syfoserviceProducer,
                                healthInformation,
                                env.syfoserviceQueueName,
                                env.sm2013AutomaticHandlingTopic,
                                receivedSykmelding,
                                kafkaproducerreceivedSykmelding
                        )
                        Status.MANUAL_PROCESSING -> handleStatusMANUALPROCESSING(
                                personV3,
                                receivedSykmelding,
                                arbeidsfordelingV1,
                                loggingMeta,
                                fellesformat,
                                ediLoggId,
                                msgId,
                                msgHead,
                                env.sm2013Apprec,
                                kafkaproducerApprec,
                                session,
                                syfoserviceProducer,
                                healthInformation,
                                env.syfoserviceQueueName,
                                validationResult,
                                kafkaManuelTaskProducer,
                                kafkaproducerreceivedSykmelding,
                                env.sm2013ManualHandlingTopic,
                                kafkaproducervalidationResult,
                                env.sm2013BehandlingsUtfallToipic
                        )

                        Status.INVALID -> handleStatusINVALID(
                                validationResult,
                                kafkaproducerreceivedSykmelding,
                                kafkaproducervalidationResult,
                                env.sm2013InvalidHandlingTopic,
                                receivedSykmelding,
                                loggingMeta,
                                fellesformat,
                                env.sm2013Apprec,
                                env.sm2013BehandlingsUtfallToipic,
                                kafkaproducerApprec,
                                ediLoggId,
                                msgId,
                                msgHead)
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

fun sendReceipt(
    apprec: Apprec,
    sm2013ApprecTopic: String,
    kafkaproducerApprec: KafkaProducer<String, Apprec>
) {
    kafkaproducerApprec.send(ProducerRecord(sm2013ApprecTopic, apprec))
}

fun sha256hashstring(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): String =
        MessageDigest.getInstance("SHA-256")
                .digest(objectMapper.writeValueAsBytes(helseOpplysningerArbeidsuforhet))
                .fold("") { str, it -> str + "%02x".format(it) }

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

fun sendValidationResult(
    validationResult: ValidationResult,
    kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    sm2013BehandlingsUtfallToipic: String,
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta
) {

    kafkaproducervalidationResult.send(
            ProducerRecord(sm2013BehandlingsUtfallToipic, receivedSykmelding.sykmelding.id, validationResult)
    )
    log.info("Validation results send to kafka {}, {}", sm2013BehandlingsUtfallToipic, fields(loggingMeta))
}

fun fnrAndDnrIsmissingFromBehandler(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.behandler.id.find { it.typeId.v == "FNR" }?.id.isNullOrBlank() &&
                healthInformation.behandler.id.find { it.typeId.v == "DNR" }?.id.isNullOrBlank()

fun arsakskodeIsmissing(healthInformation: HelseOpplysningerArbeidsuforhet): Boolean =
        healthInformation.aktivitet.periode.any { periode -> aktivitetIkkeMuligMissingArsakskode(periode.aktivitetIkkeMulig) }

fun aktivitetIkkeMuligMissingArsakskode(aktivitetIkkeMulig: HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig?): Boolean {
    return if (aktivitetIkkeMulig == null)
        false
    else if (aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode == null)
        true
    else aktivitetIkkeMulig.medisinskeArsaker != null && aktivitetIkkeMulig.medisinskeArsaker.arsakskode.any { it.v.isNullOrEmpty() }
}
