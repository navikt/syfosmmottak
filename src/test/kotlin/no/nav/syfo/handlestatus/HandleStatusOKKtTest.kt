package no.nav.syfo.handlestatus

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.utils.getFileAsString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

internal class HandleStatusOKKtTest {

    val kafkaApprecProducer = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
    val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
    val kafkaProducerReceviedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>(relaxed = true)

    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val msgHead = fellesformat.get<XMLMsgHead>()

    @BeforeEach
    internal fun `Set up`() {
        clearAllMocks()
    }

    @Test
    internal fun `test ok producer`() {
        handleStatusOK(
            fellesformat,
            "123",
            "1",
            msgHead,
            "topic",
            kafkaApprecProducer,
            LoggingMeta("1", "", ""),
            "topic", receivedSykmelding, kafkaProducerReceviedSykmelding
        )
        verify(exactly = 1) { kafkaProducerReceviedSykmelding.send(any()) }
        verify(exactly = 1) { kafkaApprecProducer.send(any()) }
    }

    @Test
    internal fun `test failing producer`() {
        val future = CompletableFuture<RecordMetadata>()

        future.completeAsync {
            throw RuntimeException()
        }

        every { kafkaProducerReceviedSykmelding.send(any()) } returns future
        val exception = assertThrows<ExecutionException> {
            handleStatusOK(
                fellesformat,
                "123",
                "1",
                msgHead,
                "topic",
                kafkaApprecProducer,
                LoggingMeta("1", "", ""),
                "topic", receivedSykmelding, kafkaProducerReceviedSykmelding
            )
        }
        Assertions.assertInstanceOf(RuntimeException::class.java, exception.cause)

        verify(exactly = 1) { kafkaProducerReceviedSykmelding.send(any()) }
        verify(exactly = 0) { kafkaApprecProducer.send(any()) }
    }

    @Test
    internal fun `test apprec producer fails`() {
        val ff = CompletableFuture<RecordMetadata>()
        ff.completeAsync {
            throw RuntimeException()
        }

        every { kafkaProducerReceviedSykmelding.send(any()) } returns CompletableFuture<RecordMetadata>().apply {
            complete(
                mockk()
            )
        }
        every { kafkaApprecProducer.send(any()) } returns ff
        val exception = assertThrows<ExecutionException> {
            handleStatusOK(
                fellesformat,
                "123",
                "1",
                msgHead,
                "topic",
                kafkaApprecProducer,
                LoggingMeta("1", "", ""),
                "topic", receivedSykmelding, kafkaProducerReceviedSykmelding
            )
        }
        Assertions.assertInstanceOf(RuntimeException::class.java, exception.cause)

        verify(exactly = 1) { kafkaProducerReceviedSykmelding.send(any()) }
        verify(exactly = 1) { kafkaApprecProducer.send(any()) }
    }
}
