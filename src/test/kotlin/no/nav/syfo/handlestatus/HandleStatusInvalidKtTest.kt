package no.nav.syfo.handlestatus

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import kotlin.test.assertFailsWith
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.utils.getFileAsString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

class HandleStatusInvalidKtTest : Spek({
    val kafkaApprecProducer = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
    val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
    val kafkaProducerReceviedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>(relaxed = true)
    val validationResultKafkaProducer = mockk<KafkaProducer<String, ValidationResult>>()
    val validationResult = ValidationResult(Status.INVALID, emptyList())
    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val msgHead = fellesformat.get<XMLMsgHead>()
    fun setUpMocks() {
        every { kafkaApprecProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { validationResultKafkaProducer.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
        every { kafkaProducerReceviedSykmelding.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
    }
    beforeEachTest { clearAllMocks() }
    describe("Test Invalid status") {
        it("Send invalid OK") {
            setUpMocks()
            handleStatusInvalid(validationResult, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, receivedSykmelding, fellesformat, kafkaApprecProducer, msgHead)

            verify(exactly = 1) { kafkaApprecProducer.send(any()) }
            verify(exactly = 1) { validationResultKafkaProducer.send(any()) }
            verify(exactly = 1) { kafkaProducerReceviedSykmelding.send(any()) }
        }

        it("Should fail when apprecProducer fails") {
            setUpMocks()
            every { kafkaApprecProducer.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                handleStatusInvalid(validationResult, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, receivedSykmelding, fellesformat, kafkaApprecProducer, msgHead)
            }
        }

        it("Should fail when validationResultProducer fails") {
            setUpMocks()
            every { validationResultKafkaProducer.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                handleStatusInvalid(validationResult, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, receivedSykmelding, fellesformat, kafkaApprecProducer, msgHead)
            }
        }
        it("Should fail when ReceivedSykmeldingKafkaProducer fails") {
            setUpMocks()
            every { kafkaProducerReceviedSykmelding.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                handleStatusInvalid(validationResult, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, receivedSykmelding, fellesformat, kafkaApprecProducer, msgHead)
            }
        }
        it("Should fail when ReceivedSykmeldingKafkaProducer fails") {
            setUpMocks()
            every { kafkaProducerReceviedSykmelding.send(any()) } returns getFailingFuture()
            assertFailsWith<ExecutionException> {
                handleStatusInvalid(validationResult, kafkaProducerReceviedSykmelding, validationResultKafkaProducer, receivedSykmelding, fellesformat, kafkaApprecProducer, msgHead)
            }
        }
    }
})

private fun handleStatusInvalid(validationResult: ValidationResult, kafkaProducerReceviedSykmelding: KafkaProducer<String, ReceivedSykmelding>, validationResultKafkaProducer: KafkaProducer<String, ValidationResult>, receivedSykmelding: ReceivedSykmelding, fellesformat: XMLEIFellesformat, kafkaApprecProducer: KafkaProducer<String, Apprec>, msgHead: XMLMsgHead) {
    handleStatusINVALID(
            validationResult,
            kafkaProducerReceviedSykmelding,
            validationResultKafkaProducer,
            "",
            receivedSykmelding,
            LoggingMeta("", "", ""),
            fellesformat,
            "",
            "",
            kafkaApprecProducer,
            "",
            "",
            msgHead
    )
}