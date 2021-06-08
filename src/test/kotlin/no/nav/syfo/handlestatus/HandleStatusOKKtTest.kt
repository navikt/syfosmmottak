package no.nav.syfo.handlestatus

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.utils.getFileAsString
import org.amshove.kluent.shouldBeInstanceOf
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.RecordMetadata
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.io.StringReader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import javax.jms.MessageProducer
import javax.jms.Session
import kotlin.test.assertFailsWith

class HandleStatusOKKtTest() : Spek({

    val kafkaApprecProducer = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
    val session = mockk<Session>(relaxed = true)
    val syfoserviceProducer = mockk<MessageProducer>(relaxed = true)
    val healthInformation = mockk<HelseOpplysningerArbeidsuforhet>(relaxed = true)
    val receivedSykmelding = mockk<ReceivedSykmelding>(relaxed = true)
    val kafkaProducerReceviedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>(relaxed = true)

    val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon2.xml")
    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat
    val msgHead = fellesformat.get<XMLMsgHead>()

    beforeEachTest { clearAllMocks() }

    describe("Test sending") {
        it("test ok producer") {
            handleStatusOK(
                fellesformat,
                "123",
                "1",
                msgHead,
                "topic",
                kafkaApprecProducer,
                LoggingMeta("1", "", ""),
                session,
                syfoserviceProducer,
                healthInformation, "", "topic", receivedSykmelding, kafkaProducerReceviedSykmelding
            )
            verify(exactly = 1) { kafkaProducerReceviedSykmelding.send(any()) }
            verify(exactly = 1) { kafkaApprecProducer.send(any()) }
            verify(exactly = 1) { syfoserviceProducer.send(any()) }
        }

        it("test failing producer") {

            val future = CompletableFuture<RecordMetadata>()

            future.completeAsync {
                throw RuntimeException()
            }

            every { kafkaProducerReceviedSykmelding.send(any()) } returns future
            val exception = assertFailsWith<ExecutionException> {
                handleStatusOK(
                    fellesformat,
                    "123",
                    "1",
                    msgHead,
                    "topic",
                    kafkaApprecProducer,
                    LoggingMeta("1", "", ""),
                    session,
                    syfoserviceProducer,
                    healthInformation, "", "topic", receivedSykmelding, kafkaProducerReceviedSykmelding
                )
            }
            exception.cause shouldBeInstanceOf RuntimeException::class

            verify(exactly = 1) { kafkaProducerReceviedSykmelding.send(any()) }
            verify(exactly = 0) { kafkaApprecProducer.send(any()) }
            verify(exactly = 0) { syfoserviceProducer.send(any()) }
        }

        it("test apprec producer fails") {
            val ff = CompletableFuture<RecordMetadata>()
            ff.completeAsync {
                throw RuntimeException()
            }

            every { kafkaProducerReceviedSykmelding.send(any()) } returns CompletableFuture<RecordMetadata>().apply { complete(mockk()) }
            every { kafkaApprecProducer.send(any()) } returns ff
            val exception = assertFailsWith<ExecutionException> {
                handleStatusOK(
                    fellesformat,
                    "123",
                    "1",
                    msgHead,
                    "topic",
                    kafkaApprecProducer,
                    LoggingMeta("1", "", ""),
                    session,
                    syfoserviceProducer,
                    healthInformation, "", "topic", receivedSykmelding, kafkaProducerReceviedSykmelding
                )
            }
            exception.cause shouldBeInstanceOf RuntimeException::class

            verify(exactly = 1) { kafkaProducerReceviedSykmelding.send(any()) }
            verify(exactly = 1) { kafkaApprecProducer.send(any()) }
            verify(exactly = 0) { syfoserviceProducer.send(any()) }
        }
    }
})
