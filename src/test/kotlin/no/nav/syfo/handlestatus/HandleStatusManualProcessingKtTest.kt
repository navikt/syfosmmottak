package no.nav.syfo.handlestatus

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import javax.jms.MessageProducer
import javax.jms.Session
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.utils.getFileAsString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
class HandleStatusManualProcessingKtTest : Spek({
    val kafkaApprecProducer = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
    val session = mockk<Session>(relaxed = true)
    val syfoserviceProducer = mockk<MessageProducer>(relaxed = true)
    val healthInformation = mockk<HelseOpplysningerArbeidsuforhet>(relaxed = true)
    val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
    val kafkaProducerReceviedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>(relaxed = true)
    val kafkaManualTaskProducer = mockk<KafkaProducer<String, ProduceTask>>()
    val validationResultKafkaProducer = mockk<KafkaProducer<String, ValidationResult>>()
    val manuellOppgaveProducer = mockk<KafkaProducer<String, ManuellOppgave>>()
    val validationResult = ValidationResult(Status.MANUAL_PROCESSING, emptyList())
    val validationResultIkkeManuell = ValidationResult(Status.MANUAL_PROCESSING, listOf(
        RuleInfo(ruleName = "SYKMELDING_MED_BEHANDLINGSDAGER",
            messageForUser = "Sykmelding inneholder behandlingsdager.",
            messageForSender = "Sykmelding inneholder behandlingsdager.",
            ruleStatus = Status.MANUAL_PROCESSING
        ))
    )
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val msgHead = fellesformat.get<XMLMsgHead>()
    val loggingMeta = LoggingMeta("", "", "")
    fun setUpMocks() {
        every { kafkaApprecProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { kafkaManualTaskProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { manuellOppgaveProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { validationResultKafkaProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { kafkaProducerReceviedSykmelding.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    }

    beforeEachTest { clearAllMocks() }

    describe("Send manual processing") {
        it("Should send to manuel") {
            setUpMocks()
            runBlocking {
                handleManualProcessing(receivedSykmelding, loggingMeta, fellesformat, msgHead, kafkaApprecProducer, session, syfoserviceProducer, healthInformation, validationResult, kafkaManualTaskProducer, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, manuellOppgaveProducer)
            }
            verify(exactly = 0) { kafkaApprecProducer.send(any()) }
            verify(exactly = 0) { kafkaManualTaskProducer.send(any()) }
            verify(exactly = 1) { manuellOppgaveProducer.send(any()) }
            verify(exactly = 0) { validationResultKafkaProducer.send(any()) }
            verify(exactly = 0) { kafkaProducerReceviedSykmelding.send(any()) }
            verify(exactly = 0) { syfoserviceProducer.send(any()) }
        }
        it("Should throw exception when kafkaproducer for manueloppgave") {
            setUpMocks()
            every { manuellOppgaveProducer.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                runBlocking {
                    handleManualProcessing(receivedSykmelding, loggingMeta, fellesformat, msgHead, kafkaApprecProducer, session, syfoserviceProducer, healthInformation, validationResult, kafkaManualTaskProducer, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, manuellOppgaveProducer)
                }
            }
        }

        it("Should throw exeption when sending ReceivedSykmelding fails") {
            setUpMocks()
            every { kafkaProducerReceviedSykmelding.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                runBlocking {
                    handleManualProcessing(receivedSykmelding, loggingMeta, fellesformat, msgHead, kafkaApprecProducer, session, syfoserviceProducer, healthInformation, validationResultIkkeManuell, kafkaManualTaskProducer, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, manuellOppgaveProducer)
                }
            }
        }

        it("Should throw exeption when sending validationResults fails") {
            setUpMocks()
            every { validationResultKafkaProducer.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                runBlocking {
                    handleManualProcessing(receivedSykmelding, loggingMeta, fellesformat, msgHead, kafkaApprecProducer, session, syfoserviceProducer, healthInformation, validationResultIkkeManuell, kafkaManualTaskProducer, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, manuellOppgaveProducer)
                }
            }
        }
        it("Should throw exeption when sending apprec fails") {
            setUpMocks()
            every { kafkaApprecProducer.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                runBlocking {
                    handleManualProcessing(receivedSykmelding, loggingMeta, fellesformat, msgHead, kafkaApprecProducer, session, syfoserviceProducer, healthInformation, validationResultIkkeManuell, kafkaManualTaskProducer, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, manuellOppgaveProducer)
                }
            }
        }

        it("should throw exception when kafkaManualTaskProducer fails") {
            setUpMocks()
            every { kafkaManualTaskProducer.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                runBlocking {
                    handleManualProcessing(receivedSykmelding, loggingMeta, fellesformat, msgHead, kafkaApprecProducer, session, syfoserviceProducer, healthInformation, validationResultIkkeManuell, kafkaManualTaskProducer, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, manuellOppgaveProducer)
                }
            }
        }
    }
})

fun getFailingFuture(): CompletableFuture<RecordMetadata> {
    val future = CompletableFuture<RecordMetadata>()

    future.completeAsync {
        throw RuntimeException()
    }
    return future
}

private fun handleManualProcessing(receivedSykmelding: ReceivedSykmelding, loggingMeta: LoggingMeta, fellesformat: XMLEIFellesformat, msgHead: XMLMsgHead, kafkaApprecProducer: KafkaProducer<String, Apprec>, session: Session, syfoserviceProducer: MessageProducer, healthInformation: HelseOpplysningerArbeidsuforhet, validationResutl: ValidationResult, kafkaManualTaskProducer: KafkaProducer<String, ProduceTask>, kafkaProducerReceviedSykmelding: KafkaProducer<String, ReceivedSykmelding>, validationResultKafkaProducer: KafkaProducer<String, ValidationResult>, manuellOppgaveProducer: KafkaProducer<String, ManuellOppgave>) {
    handleStatusMANUALPROCESSING(
            receivedSykmelding,
            loggingMeta,
            fellesformat,
            "",
            "",
            msgHead,
            "",
            kafkaApprecProducer,
            session,
            syfoserviceProducer,
            healthInformation,
            "",
            validationResutl,
            kafkaManualTaskProducer,
            kafkaProducerReceviedSykmelding,
            "",
            validationResultKafkaProducer,
            "",
            manuellOppgaveProducer,
            ""
    )
}
