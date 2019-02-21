package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.migesok.jaxb.adapter.javatime.LocalDateTimeXmlAdapter
import com.migesok.jaxb.adapter.javatime.LocalDateXmlAdapter
import io.ktor.application.Application
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.io.core.toByteArray
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.apprec._2004_11_21.XMLAppRec
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.paop.ws.configureBasicAuthFor
import no.nav.syfo.client.Status
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.apprec.ApprecError
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.apprec.findApprecError
import no.nav.syfo.apprec.toApprecCV
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.util.connectionFactory
import no.nav.syfo.util.readProducerConfig
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.apache.cxf.ws.addressing.WSAddressingFeature
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import javax.jms.Connection
import javax.jms.Queue
import javax.jms.Session
import javax.jms.TextMessage
import org.apache.kafka.clients.producer.ProducerRecord
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisSentinelPool
import redis.clients.jedis.exceptions.JedisConnectionException
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import java.security.MessageDigest
import java.time.LocalDate
import java.util.Base64
import java.util.concurrent.Executors
import javax.jms.MessageProducer
import javax.xml.bind.JAXBContext
import javax.xml.bind.Marshaller
import javax.xml.bind.Unmarshaller

fun doReadynessCheck(): Boolean {
    return true
}

val objectMapper: ObjectMapper = ObjectMapper()
        .registerModule(JavaTimeModule())
        .registerKotlinModule()
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)

val fellesformatJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLMsgHead::class.java, HelseOpplysningerArbeidsuforhet::class.java)
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller().apply {
    setAdapter(LocalDateTimeXmlAdapter::class.java, XMLDateTimeAdapter())
    setAdapter(LocalDateXmlAdapter::class.java, XMLDateAdapter())
}

val apprecJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLAppRec::class.java)
val apprecMarshaller: Marshaller = apprecJaxBContext.createMarshaller()

val redisMasterName = "mymaster"
val redisHost = "rfs-redis-syfosmmottak" // TODO: Do this properly with naiserator

data class ApplicationState(var running: Boolean = true)

val log = LoggerFactory.getLogger("nav.syfosmmottak-application")

@KtorExperimentalAPI
fun main(args: Array<String>) = runBlocking(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
    val config: ApplicationConfig = objectMapper.readValue(File(System.getenv("CONFIG_FILE")))
    val applicationState = ApplicationState()
    val credentials: VaultCredentials = objectMapper.readValue(vaultApplicationPropertiesPath.toFile())

    val applicationServer = embeddedServer(Netty, config.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })

    connectionFactory(config).createConnection(credentials.mqUsername, credentials.mqPassword).use { connection ->
        connection.start()
        JedisSentinelPool(redisMasterName, setOf("$redisHost:26379")).resource.use { jedis ->
            val session = connection.createSession()
            val inputQueue = session.createQueue(config.inputQueueName)
            val receiptQueue = session.createQueue(config.apprecQueueName)
            val syfoserviceQueue = session.createQueue(config.syfoserviceQueueName)
            val backoutQueue = session.createQueue(config.inputBackoutQueueName)
            session.close()

            val producerProperties = readProducerConfig(config, credentials, valueSerializer = JacksonKafkaSerializer::class)
            val kafkaproducer = KafkaProducer<String, ReceivedSykmelding>(producerProperties)

            val syfoSykemeldingRuleClient = SyfoSykemeldingRuleClient(config.syfosmreglerApiUrl, credentials)

            val sarClient = SarClient(config.kuhrSarApiUrl, credentials)

            val oidcClient = StsOidcClient(credentials.serviceuserUsername, credentials.serviceuserPassword)
            val aktoerIdClient = AktoerIdClient(config.aktoerregisterV1Url, oidcClient)

            val subscriptionEmottak = JaxWsProxyFactoryBean().apply {
                address = config.subscriptionEndpointURL
                features.add(WSAddressingFeature())
                serviceClass = SubscriptionPort::class.java
            }.create() as SubscriptionPort
            configureBasicAuthFor(subscriptionEmottak, credentials.serviceuserUsername, credentials.serviceuserPassword)

            listen(inputQueue, receiptQueue, backoutQueue, syfoserviceQueue, connection, subscriptionEmottak, kafkaproducer, syfoSykemeldingRuleClient, sarClient, aktoerIdClient, config, credentials, applicationState, jedis).join()
        }
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}

@KtorExperimentalAPI
fun CoroutineScope.listen(
    inputQueue: Queue,
    receiptQueue: Queue,
    backoutQueue: Queue,
    syfoserviceQueue: Queue,
    connection: Connection,
    subscriptionEmottak: SubscriptionPort,
    kafkaproducer: KafkaProducer<String, ReceivedSykmelding>,
    syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    kuhrSarClient: SarClient,
    aktoerIdClient: AktoerIdClient,
    config: ApplicationConfig,
    credentials: VaultCredentials,
    applicationState: ApplicationState,
    jedis: Jedis
) = launch {
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val consumer = session.createConsumer(inputQueue)
    val receiptProducer = session.createProducer(receiptQueue)
    val syfoserviceProducer = session.createProducer(syfoserviceQueue)
    val backoutProducer = session.createProducer(backoutQueue)

    while (applicationState.running) {
        val message = consumer.receiveNoWait()
        if (message == null) {
            delay(100)
            continue
        }

        var logValues = arrayOf(
                keyValue("smId", "missing"),
                keyValue("organizationNumber", "missing"),
                keyValue("msgId", "missing")
        )

        val logKeys = logValues.joinToString(prefix = "(", postfix = ")", separator = ",") {
            "{}"
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
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val msgHead = fellesformat.get<XMLMsgHead>()
            val ediLoggId = receiverBlock.ediLoggId
            val sha256String = sha256hashstring(healthInformation)
            val msgId = msgHead.msgInfo.msgId
            val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
            val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
            val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
            val legekontorOrgName = msgHead.msgInfo.sender.organisation.organisationName

            val personNumberPatient = healthInformation.pasient.fodselsnummer.id
            val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur

            logValues = arrayOf(
                    keyValue("smId", ediLoggId),
                    keyValue("organizationNumber", legekontorOrgNr),
                    keyValue("msgId", msgId)
            )

            log.info("Received message, $logKeys", *logValues)

            val aktoerIdsDeferred = aktoerIdClient.getAktoerIds(listOf(personNumberDoctor, personNumberPatient), msgId, credentials.serviceuserUsername)
            val samhandlerInfoDeferred = kuhrSarClient.getSamhandler(personNumberDoctor)

            val aktoerIds = aktoerIdsDeferred
            val samhandlerPraksis = findBestSamhandlerPraksis(samhandlerInfoDeferred, legekontorOrgName)?.samhandlerPraksis

            // TODO comment out this when going into prod-prod
            /*
            subscriptionEmottak.startSubscription(StartSubscriptionRequest().apply {
                key = samhandlerPraksis?.tss_ident
                data = msgHead.msgInfo.sender.toString().toByteArray()
                partnerid = receiverBlock.partnerReferanse.toInt()
            })
            */

            try {
                val redisEdiLoggId = jedis.get(sha256String)

                if (redisEdiLoggId != null) {
                    log.warn("Message with {} marked as duplicate $logKeys", keyValue("originalEdiLoggId", redisEdiLoggId), *logValues)
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(ApprecError.DUPLICATE))
                    log.info("Apprec Receipt sent to {} $logKeys", config.apprecQueueName, *logValues)
                    continue
                } else {
                    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
                }
            } catch (connectionException: JedisConnectionException) {
                log.warn("Unable to contact redis, will allow possible duplicates.", connectionException)
            }

            val sykmelding = healthInformation.toSykmelding(
                    sykmeldingId = msgId,
                    pasientAktoerId = aktoerIds[personNumberPatient]!!.identer!!.first().ident,
                    legeAktoerId = aktoerIds[personNumberDoctor]!!.identer!!.first().ident
            )
            val receivedSykmelding = ReceivedSykmelding(
                    sykmelding = sykmelding,
                    personNrPasient = personNumberPatient,
                    personNrLege = personNumberDoctor,
                    navLogId = ediLoggId,
                    msgId = msgId,
                    legekontorOrgNr = legekontorOrgNr,
                    legekontorOrgName = legekontorOrgName,
                    legekontorHerId = legekontorHerId,
                    legekontorReshId = legekontorReshId,
                    mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime().toLocalDateTime(),
                    signaturDato = msgHead.msgInfo.genDate,
                    rulesetVersion = healthInformation.regelSettVersjon,
                    fellesformat = inputMessageText
            )

            log.info("Validating against rules, $logKeys", *logValues)
            val validationResult = syfoSykemeldingRuleClient.executeRuleValidation(receivedSykmelding).await()
            when {
                validationResult.status == Status.OK -> {
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
                    log.info("Apprec Receipt sent to {} $logKeys", config.apprecQueueName, *logValues)
                    kafkaproducer.send(ProducerRecord(config.sm2013AutomaticHandlingTopic, receivedSykmelding))
                    log.info("Message send to kafka {} $logKeys", config.sm2013AutomaticHandlingTopic, *logValues)
                    notifySyfoService(session, syfoserviceProducer, ediLoggId, healthInformation)
                    log.info("Message send to syfo {} $logKeys", config.syfoserviceQueueName, *logValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $logKeys has outcome automatic, processing took {}s", *logValues,
                            currentRequestLatency)
                }
                validationResult.status == Status.MANUAL_PROCESSING -> {
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
                    log.info("Apprec Receipt sent to {} $logKeys", config.apprecQueueName, *logValues)
                    kafkaproducer.send(ProducerRecord(config.sm2013ManualHandlingTopic, receivedSykmelding))
                    log.info("Message send to kafka {} $logKeys", config.sm2013ManualHandlingTopic, *logValues)
                    notifySyfoService(session, syfoserviceProducer, ediLoggId, healthInformation)
                    log.info("Message send to syfo {} $logKeys", config.syfoserviceQueueName, *logValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $logKeys has outcome manual processing, processing took {}s", *logValues,
                            currentRequestLatency)
                    log.info("Message $logKeys Rules hits {}", *logValues, validationResult.ruleHits)
                }
                validationResult.status == Status.INVALID -> {
                    val apprecErrors = findApprecError(validationResult.ruleHits)
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, apprecErrors)
                    log.info("Apprec Receipt sent to {} $logKeys", config.apprecQueueName, *logValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $logKeys has outcome return, processing took {}s", *logValues,
                            currentRequestLatency)
                    log.info("Message $logKeys Rules hits {}", *logValues, validationResult.ruleHits)
                }
            }
        } catch (e: Exception) {
            log.error("Exception caught while handling message, sending to backout $logKeys", *logValues, e)
            backoutProducer.send(message)
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
    session: Session,
    receiptProducer: MessageProducer,
    fellesformat: XMLEIFellesformat,
    apprecStatus: ApprecStatus,
    apprecErrors: List<ApprecError> = listOf()
) {
    receiptProducer.send(session.createTextMessage().apply {
        val apprec = createApprec(fellesformat, apprecStatus)
        apprec.get<XMLAppRec>().error.addAll(apprecErrors.map { it.toApprecCV() })
        text = apprecMarshaller.toString(apprec)
    })
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

// TODO this does not work
fun notifySyfoService(
    session: Session,
    receiptProducer: MessageProducer,
    ediLoggId: String,
    healthInformation: HelseOpplysningerArbeidsuforhet

) {
    receiptProducer.send(session.createTextMessage().apply {
        val syketilfelleStartDato = extractSyketilfelleStartDato(healthInformation)
        val sykmelding = convertSykemeldingToBase64(healthInformation)
        val syfo = Syfo(tilleggsdata = Tilleggsdata(ediLoggId = ediLoggId, syketilfelleStartDato = syketilfelleStartDato), sykmelding = sykmelding)
        text = syfo.toString()
    })
}

data class Syfo(
    val tilleggsdata: Tilleggsdata,
    val sykmelding: ByteArray
)

data class Tilleggsdata(
    val ediLoggId: String,
    val syketilfelleStartDato: LocalDate
)

fun extractSyketilfelleStartDato(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): LocalDate =
        helseOpplysningerArbeidsuforhet.syketilfelleStartDato

fun convertSykemeldingToBase64(helseOpplysningerArbeidsuforhet: HelseOpplysningerArbeidsuforhet): ByteArray =
        Base64.getEncoder().encode(helseOpplysningerArbeidsuforhet.toString().toByteArray(Charsets.ISO_8859_1))
