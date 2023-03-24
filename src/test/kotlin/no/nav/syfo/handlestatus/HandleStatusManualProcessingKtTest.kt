package no.nav.syfo.handlestatus

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.RuleInfo
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.utils.getFileAsString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

internal class HandleStatusManualProcessingKtTest {
    val kafkaApprecProducer = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
    val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
    val kafkaProducerReceviedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>(relaxed = true)
    val kafkaManualTaskProducer = mockk<KafkaProducer<String, OpprettOppgaveKafkaMessage>>()
    val validationResultKafkaProducer = mockk<KafkaProducer<String, ValidationResult>>()
    val manuellOppgaveProducer = mockk<KafkaProducer<String, ManuellOppgave>>()
    val validationResult = ValidationResult(Status.MANUAL_PROCESSING, emptyList())
    val validationResultIkkeManuell = ValidationResult(
        Status.MANUAL_PROCESSING,
        listOf(
            RuleInfo(
                ruleName = "SYKMELDING_MED_BEHANDLINGSDAGER",
                messageForUser = "Sykmelding inneholder behandlingsdager.",
                messageForSender = "Sykmelding inneholder behandlingsdager.",
                ruleStatus = Status.MANUAL_PROCESSING,
            ),
        ),
    )
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val msgHead = fellesformat.get<XMLMsgHead>()
    val loggingMeta = LoggingMeta("", "", "")
    fun setUpMocks() {
        every { kafkaApprecProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { kafkaManualTaskProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { manuellOppgaveProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { validationResultKafkaProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply {
            complete(
                mockk(),
            )
        }
        every { kafkaProducerReceviedSykmelding.send(any()) } returns CompletableFuture<RecordMetadata>().apply {
            complete(
                mockk(),
            )
        }
    }

    @BeforeEach
    internal fun `Set up`() {
        clearAllMocks()
    }

    @Test
    internal fun `Should send to manuel`() {
        setUpMocks()

        handleManualProcessing(
            receivedSykmelding,
            loggingMeta,
            fellesformat,
            msgHead,
            kafkaApprecProducer,
            validationResult,
            kafkaManualTaskProducer,
            kafkaProducerReceviedSykmelding,
            validationResultKafkaProducer,
            manuellOppgaveProducer,
        )

        verify(exactly = 0) { kafkaApprecProducer.send(any()) }
        verify(exactly = 0) { kafkaManualTaskProducer.send(any()) }
        verify(exactly = 1) { manuellOppgaveProducer.send(any()) }
        verify(exactly = 0) { validationResultKafkaProducer.send(any()) }
        verify(exactly = 0) { kafkaProducerReceviedSykmelding.send(any()) }
    }

    @Test
    internal fun `Should throw exception when kafkaproducer for manueloppgave`() {
        setUpMocks()
        every { manuellOppgaveProducer.send(any()) } returns getFailingFuture()
        assertThrows<ExecutionException> {
            runBlocking {
                handleManualProcessing(
                    receivedSykmelding,
                    loggingMeta,
                    fellesformat,
                    msgHead,
                    kafkaApprecProducer,
                    validationResult,
                    kafkaManualTaskProducer,
                    kafkaProducerReceviedSykmelding,
                    validationResultKafkaProducer,
                    manuellOppgaveProducer,
                )
            }
        }
    }

    @Test
    internal fun `Should throw exeption when sending ReceivedSykmelding fails`() {
        setUpMocks()
        every { kafkaProducerReceviedSykmelding.send(any()) } returns getFailingFuture()
        assertThrows<ExecutionException> {
            runBlocking {
                handleManualProcessing(
                    receivedSykmelding,
                    loggingMeta,
                    fellesformat,
                    msgHead,
                    kafkaApprecProducer,
                    validationResultIkkeManuell,
                    kafkaManualTaskProducer,
                    kafkaProducerReceviedSykmelding,
                    validationResultKafkaProducer,
                    manuellOppgaveProducer,
                )
            }
        }
    }

    @Test
    internal fun `Should throw exeption when sending validationResults fails`() {
        setUpMocks()
        every { validationResultKafkaProducer.send(any()) } returns getFailingFuture()
        assertThrows<ExecutionException> {
            runBlocking {
                handleManualProcessing(
                    receivedSykmelding,
                    loggingMeta,
                    fellesformat,
                    msgHead,
                    kafkaApprecProducer,
                    validationResultIkkeManuell,
                    kafkaManualTaskProducer,
                    kafkaProducerReceviedSykmelding,
                    validationResultKafkaProducer,
                    manuellOppgaveProducer,
                )
            }
        }
    }

    @Test
    internal fun `Should throw exeption when sending apprec fails`() {
        setUpMocks()
        every { kafkaApprecProducer.send(any()) } returns getFailingFuture()
        assertThrows<ExecutionException> {
            runBlocking {
                handleManualProcessing(
                    receivedSykmelding,
                    loggingMeta,
                    fellesformat,
                    msgHead,
                    kafkaApprecProducer,
                    validationResultIkkeManuell,
                    kafkaManualTaskProducer,
                    kafkaProducerReceviedSykmelding,
                    validationResultKafkaProducer,
                    manuellOppgaveProducer,
                )
            }
        }
    }

    @Test
    internal fun `Should throw exception when kafkaManualTaskProducer fails`() {
        setUpMocks()
        every { kafkaManualTaskProducer.send(any()) } returns getFailingFuture()
        assertThrows<ExecutionException> {
            runBlocking {
                handleManualProcessing(
                    receivedSykmelding,
                    loggingMeta,
                    fellesformat,
                    msgHead,
                    kafkaApprecProducer,
                    validationResultIkkeManuell,
                    kafkaManualTaskProducer,
                    kafkaProducerReceviedSykmelding,
                    validationResultKafkaProducer,
                    manuellOppgaveProducer,
                )
            }
        }
    }
}

fun getFailingFuture(): CompletableFuture<RecordMetadata> {
    val future = CompletableFuture<RecordMetadata>()

    future.completeAsync {
        throw RuntimeException()
    }
    return future
}

private fun handleManualProcessing(
    receivedSykmelding: ReceivedSykmelding,
    loggingMeta: LoggingMeta,
    fellesformat: XMLEIFellesformat,
    msgHead: XMLMsgHead,
    kafkaApprecProducer: KafkaProducer<String, Apprec>,
    validationResutl: ValidationResult,
    kafkaManualTaskProducer: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    kafkaProducerReceviedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    validationResultKafkaProducer: KafkaProducer<String, ValidationResult>,
    manuellOppgaveProducer: KafkaProducer<String, ManuellOppgave>,
) {
    handleStatusMANUALPROCESSING(
        receivedSykmelding,
        loggingMeta,
        fellesformat,
        "",
        "",
        msgHead,
        "",
        kafkaApprecProducer,
        validationResutl,
        kafkaManualTaskProducer,
        kafkaProducerReceviedSykmelding,
        "",
        validationResultKafkaProducer,
        "",
        manuellOppgaveProducer,
        "",
        "",
    )
}
