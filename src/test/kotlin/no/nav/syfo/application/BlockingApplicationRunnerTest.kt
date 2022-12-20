package no.nav.syfo.application

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.service.DuplicationService
import no.nav.syfo.service.VirusScanService
import no.nav.syfo.utils.getFileAsString
import no.nav.syfo.vedlegg.google.BucketUploadService
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.TextMessage

class BlockingApplicationRunnerTest : FunSpec({
    val inputconsumer = mockk<MessageConsumer>(relaxed = true)
    val backoutProducer = mockk<MessageProducer>(relaxed = true)
    val env = mockk<Environment>(relaxed = true)
    val applicationState = mockk<ApplicationState>()
    val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
    val syfoSykemeldingRuleClient = mockk<SyfoSykemeldingRuleClient>()
    val norskHelsenettClient = mockk<NorskHelsenettClient>()
    val kuhrSarClient = mockk<SarClient>()
    val pdlPersonService = mockk<PdlPersonService>()
    val bucketUploadService = mockk<BucketUploadService>(relaxed = true)
    val kafkaproducerreceivedSykmelding = mockk<KafkaProducer<String, ReceivedSykmelding>>(relaxed = true)
    val kafkaproducervalidationResult = mockk<KafkaProducer<String, ValidationResult>>(relaxed = true)
    val kafkaManuelTaskProducer = mockk<KafkaProducer<String, OpprettOppgaveKafkaMessage>>(relaxed = true)
    val kafkaproducerApprec = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
    val kafkaproducerManuellOppgave = mockk<KafkaProducer<String, ManuellOppgave>>(relaxed = true)
    val virusScanService = mockk<VirusScanService>(relaxed = true)
    val duplicationService = mockk<DuplicationService>(relaxed = true)

    val blockingApplicationRunner = BlockingApplicationRunner(
        env,
        applicationState,
        emottakSubscriptionClient,
        syfoSykemeldingRuleClient,
        norskHelsenettClient,
        kuhrSarClient,
        pdlPersonService,
        bucketUploadService,
        kafkaproducerreceivedSykmelding,
        kafkaproducervalidationResult,
        kafkaManuelTaskProducer,
        kafkaproducerApprec,
        kafkaproducerManuellOppgave,
        virusScanService,
        duplicationService
    )

    beforeTest {
        clearMocks(kafkaproducerApprec, kafkaproducerreceivedSykmelding)
    }

    coEvery { pdlPersonService.getIdenter(any(), any()) } returns mapOf(
        "10987654321" to PdlPerson(listOf(PdlIdent("10987654321", false, "FOLKEREGISTERIDENT"), PdlIdent("aktorId", false, "AKTORID"))),
        "12345678912" to PdlPerson(listOf(PdlIdent("12345678912", false, "FOLKEREGISTERIDENT"), PdlIdent("aktorId2", false, "AKTORID")))
    )
    coEvery { kuhrSarClient.getSamhandler(any(), any()) } returns emptyList()
    coEvery { norskHelsenettClient.getByFnr(any(), any()) } returns Behandler(emptyList(), "", "HPR", null, null, null)
    coEvery { norskHelsenettClient.getByHpr(any(), any()) } returns Behandler(emptyList(), "", "HPR", null, null, null)
    coEvery { syfoSykemeldingRuleClient.executeRuleValidation(any(), any()) } returns ValidationResult(Status.OK, emptyList())
    coEvery { duplicationService.getDuplicationCheck(any(), any()) } returns null

    context("Mottak av sykmelding") {
        test("Vanlig sykmelding skal gi ok apprec") {
            every { applicationState.ready } returns true andThen false
            val stringInput = getFileAsString("src/test/resources/fellesformat.xml")
            val textMessage = mockk<TextMessage>(relaxed = true)
            every { textMessage.text } returns stringInput
            every { inputconsumer.receive(1000) } returns textMessage

            blockingApplicationRunner.run(inputconsumer, backoutProducer)

            coVerify { kafkaproducerApprec.send(match { it.value().apprecStatus == ApprecStatus.OK }) }
        }
        test("Virksomhetsykmelding skal gi ok apprec") {
            every { applicationState.ready } returns true andThen false
            val stringInput = getFileAsString("src/test/resources/sykmelding_virksomhet.xml")
            val textMessage = mockk<TextMessage>(relaxed = true)
            every { textMessage.text } returns stringInput
            every { inputconsumer.receive(1000) } returns textMessage
            coEvery { norskHelsenettClient.getByHpr(any(), any()) } returns Behandler(emptyList(), "12345678912", "HPR", null, null, null)

            blockingApplicationRunner.run(inputconsumer, backoutProducer)

            coVerify {
                kafkaproducerApprec.send(
                    match {
                        it.value().apprecStatus == ApprecStatus.OK &&
                            it.value().mottakerOrganisasjon.navn == "Helseforetak 1"
                    }
                )
            }
            coVerify {
                kafkaproducerreceivedSykmelding.send(
                    match {
                        it.value().personNrLege == "12345678912" &&
                            it.value().legeHprNr == "HPR" && it.value().sykmelding.behandler.fnr == "behandlerfnr" &&
                            it.value().legekontorOrgNr == "123456789"
                    }
                )
            }
        }
        test("Sykmelding med regelsettversjon 3 skal gi ok apprec") {
            every { applicationState.ready } returns true andThen false
            val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon3.xml")
            val textMessage = mockk<TextMessage>(relaxed = true)
            every { textMessage.text } returns stringInput
            every { inputconsumer.receive(1000) } returns textMessage

            blockingApplicationRunner.run(inputconsumer, backoutProducer)

            coVerify { kafkaproducerApprec.send(match { it.value().apprecStatus == ApprecStatus.OK }) }
        }

        test("Sykmelding med melding er ikke byte message eller text message skal gi RuntimeException") {
            every { applicationState.ready } returns true andThen false
            val textMessage = mockk<TextMessage>(relaxed = true)
            every { textMessage.text } returns null
            every { inputconsumer.receive(1000) } returns textMessage

            try {
                blockingApplicationRunner.run(inputconsumer, backoutProducer)
            } catch (exception: Exception) {
                exception.message shouldBeEqualTo "Incoming message needs to be a byte message or text message"
            }
        }
    }
})
