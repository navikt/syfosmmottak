package no.nav.syfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.Application
import io.ktor.client.HttpClient
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.kith.xmlstds.apprec._2004_11_21.XMLAppRec
import no.kith.xmlstds.msghead._2006_05_24.XMLIdent
import no.kith.xmlstds.msghead._2006_05_24.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.api.Status
import no.nav.syfo.api.createHttpClient
import no.nav.syfo.api.executeRuleValidation
import no.nav.syfo.api.registerNaisApi
import no.nav.syfo.apprec.ApprecError
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.apprec.createApprec
import no.nav.syfo.apprec.findApprecError
import no.nav.syfo.apprec.toApprecCV
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.connectionFactory
import no.nav.syfo.util.readProducerConfig
import no.trygdeetaten.xml.eiff._1.XMLEIFellesformat
import no.trygdeetaten.xml.eiff._1.XMLMottakenhetBlokk
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
import java.io.StringReader
import java.io.StringWriter
import java.security.MessageDigest
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
val fellesformatUnmarshaller: Unmarshaller = fellesformatJaxBContext.createUnmarshaller()

val apprecJaxBContext: JAXBContext = JAXBContext.newInstance(XMLEIFellesformat::class.java, XMLAppRec::class.java)
val apprecMarshaller: Marshaller = apprecJaxBContext.createMarshaller()

val redisMasterName = "mymaster"

data class ApplicationState(var running: Boolean = true)

private val log = LoggerFactory.getLogger("nav.syfosmmottak-application")

fun main(args: Array<String>) = runBlocking<Unit>(Executors.newFixedThreadPool(4).asCoroutineDispatcher()) {
    val env = Environment()
    val applicationState = ApplicationState()

    val applicationServer = embeddedServer(Netty, env.applicationPort) {
        initRouting(applicationState)
    }.start(wait = false)

    Runtime.getRuntime().addShutdownHook(Thread {
        applicationServer.stop(10, 10, TimeUnit.SECONDS)
    })

    connectionFactory(env).createConnection(env.srvappserverUsername, env.srvappserverPassword).use { connection ->
        connection.start()
        JedisSentinelPool(redisMasterName, setOf("${env.redisHost}:26379")).resource.use {
            jedis ->
            val session = connection.createSession()
            val inputQueue = session.createQueue(env.syfosmmottakinputQueueName)
            val receiptQueue = session.createQueue(env.apprecQueue)
            val backoutQueue = session.createQueue("Q1_SYFOSMMOTTAK.INPUT_BOQ") // TODO: Resolve differently when finished
            session.close()

            val producerProperties = readProducerConfig(env, valueSerializer = JacksonKafkaSerializer::class)
            val kafkaproducer = KafkaProducer<String, ReceivedSykmelding>(producerProperties)

            val httpClient = createHttpClient(env)

            val oidcClient = StsOidcClient(env.srvSyfoSmMottakUsername, env.srvSyfoSMMottakPassword)
            val aktoerIdClient = AktoerIdClient(env.aktoerregisterV1Url, oidcClient)

            listen(inputQueue, receiptQueue, backoutQueue, connection, kafkaproducer, httpClient, aktoerIdClient, env, applicationState, jedis).join()
        }
    }
}

fun Application.initRouting(applicationState: ApplicationState) {
    routing {
        registerNaisApi(readynessCheck = ::doReadynessCheck, livenessCheck = { applicationState.running })
    }
}

fun CoroutineScope.listen(
    inputQueue: Queue,
    receiptQueue: Queue,
    backoutQueue: Queue,
    connection: Connection,
    kafkaproducer: KafkaProducer<String, ReceivedSykmelding>,
    httpClient: HttpClient,
    aktoerIdClient: AktoerIdClient,
    env: Environment,
    applicationState: ApplicationState,
    jedis: Jedis
) = launch {
    val session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE)
    val consumer = session.createConsumer(inputQueue)
    val receiptProducer = session.createProducer(receiptQueue)
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
            val requestLatency = REQUEST_TIME.startTimer()
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
            val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val msgHead = fellesformat.get<XMLMsgHead>()
            val ediLoggId = receiverBlock.ediLoggId
            val sha256String = sha256hashstring(healthInformation)
            val msgId = msgHead.msgInfo.msgId
            val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id!!
            val legekontorOrgName = msgHead.msgInfo.sender.organisation.organisationName

            val personNumberPatient = healthInformation.pasient.fodselsnummer.id
            val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur
            val aktoerIds = aktoerIdClient.getAktoerIds(listOf(personNumberDoctor, personNumberPatient), msgId)

            logValues = arrayOf(
                    keyValue("smId", ediLoggId),
                    keyValue("organizationNumber", legekontorOrgNr),
                    keyValue("msgId", msgId)
            )

            log.info("Received a SM2013, $logKeys", *logValues)

            if (log.isDebugEnabled) {
                log.debug("Incoming message {}, $logKeys", inputMessageText, *logValues)
            }

            try {
                val redisEdiLoggId = jedis.get(sha256String)
                val duplicate = redisEdiLoggId != null

                if (duplicate) {
                    log.warn("Message marked as duplicate $logKeys", redisEdiLoggId, *logValues)
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, listOf(ApprecError.DUPLICATE))
                    log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueue, *logValues)
                    continue
                } else if (ediLoggId != null) {
                    jedis.setex(sha256String, TimeUnit.DAYS.toSeconds(7).toInt(), ediLoggId)
                }
            } catch (connectionException: JedisConnectionException) {
                log.warn("Unable to contact redis, will allow possible duplicates.", connectionException)
            }

            val receivedSykmelding = ReceivedSykmelding(
                    sykmelding = healthInformation,
                    aktoerIdPasient = aktoerIds[personNumberPatient]!!.identer!!.first().ident,
                    personNrPasient = personNumberPatient,
                    aktoerIdLege = aktoerIds[personNumberDoctor]!!.identer!!.first().ident,
                    personNrLege = personNumberDoctor,
                    navLogId = ediLoggId,
                    msgId = msgId,
                    legekontorOrgNr = legekontorOrgNr,
                    legekontorOrgName = legekontorOrgName,
                    mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime().toLocalDateTime(),
                    signaturDato = msgHead.msgInfo.genDate,
                    fellesformat = inputMessageText
            )

            val validationResult = httpClient.executeRuleValidation(env, receivedSykmelding)
            when {
                validationResult.status == Status.OK -> {
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
                    log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueue, *logValues)
                    kafkaproducer.send(ProducerRecord(env.sm2013AutomaticHandlingTopic, receivedSykmelding))
                    log.info("Message send to kafka {} $logKeys", env.sm2013AutomaticHandlingTopic, *logValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $logKeys has outcome automatic, processing took {}s",
                            currentRequestLatency, *logValues)
                }
                validationResult.status == Status.MANUAL_PROCESSING -> {
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.ok)
                    log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueue, *logValues)
                    kafkaproducer.send(ProducerRecord(env.sm2013ManualHandlingTopic, receivedSykmelding))
                    log.info("Message send to kafka {} $logKeys", env.sm2013ManualHandlingTopic, *logValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $logKeys has outcome manual processing, processing took {}s",
                            currentRequestLatency, *logValues)
                    log.info("Message $logKeys Rules hits {}", validationResult.ruleHits, *logValues)
                }
                validationResult.status == Status.INVALID -> {
                    val apprecErrors = findApprecError(validationResult.ruleHits)
                    sendReceipt(session, receiptProducer, fellesformat, ApprecStatus.avvist, apprecErrors)
                    log.info("Apprec Receipt sent to {} $logKeys", env.apprecQueue, *logValues)
                    val currentRequestLatency = requestLatency.observeDuration()
                    log.info("Message $logKeys has outcome return, processing took {}s",
                            currentRequestLatency, *logValues)
                    log.info("Message $logKeys Rules hits {}", validationResult.ruleHits, *logValues)
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
