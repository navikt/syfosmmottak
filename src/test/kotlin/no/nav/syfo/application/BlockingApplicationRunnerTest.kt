package no.nav.syfo.application

import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.TextMessage
import kotlinx.coroutines.runBlocking
import no.nav.syfo.ApplicationState
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.apprec.ApprecStatus
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SmtssClient
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
import org.apache.kafka.clients.producer.KafkaProducer
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class BlockingApplicationRunnerTest {
    val inputconsumer = mockk<MessageConsumer>(relaxed = true)
    val backoutProducer = mockk<MessageProducer>(relaxed = true)
    val env = mockk<EnvironmentVariables>(relaxed = true)
    val applicationState = mockk<ApplicationState>()
    val emottakSubscriptionClient = mockk<EmottakSubscriptionClient>()
    val syfoSykemeldingRuleClient = mockk<SyfoSykemeldingRuleClient>()
    val norskHelsenettClient = mockk<NorskHelsenettClient>()
    val pdlPersonService = mockk<PdlPersonService>()
    val bucketUploadService = mockk<BucketUploadService>(relaxed = true)
    val kafkaproducerreceivedSykmelding =
        mockk<KafkaProducer<String, ReceivedSykmelding>>(relaxed = true)
    val kafkaproducervalidationResult =
        mockk<KafkaProducer<String, ValidationResult>>(relaxed = true)
    val kafkaManuelTaskProducer =
        mockk<KafkaProducer<String, OpprettOppgaveKafkaMessage>>(relaxed = true)
    val kafkaproducerApprec = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
    val kafkaproducerManuellOppgave = mockk<KafkaProducer<String, ManuellOppgave>>(relaxed = true)
    val virusScanService = mockk<VirusScanService>(relaxed = true)
    val duplicationService = mockk<DuplicationService>(relaxed = true)
    val smtssClient = mockk<SmtssClient>()

    val blockingApplicationRunner =
        BlockingApplicationRunner(
            env,
            applicationState,
            emottakSubscriptionClient,
            syfoSykemeldingRuleClient,
            norskHelsenettClient,
            pdlPersonService,
            bucketUploadService,
            kafkaproducerreceivedSykmelding,
            kafkaproducervalidationResult,
            kafkaManuelTaskProducer,
            kafkaproducerApprec,
            kafkaproducerManuellOppgave,
            virusScanService,
            duplicationService,
            smtssClient,
        )

    @BeforeEach
    internal fun `Set up`() {
        clearMocks(kafkaproducerApprec, kafkaproducerreceivedSykmelding)
        coEvery { pdlPersonService.getIdenter(any(), any()) } returns
            mapOf(
                "10987654321" to
                    PdlPerson(
                        listOf(
                            PdlIdent("10987654321", false, "FOLKEREGISTERIDENT"),
                            PdlIdent("aktorId", false, "AKTORID"),
                        ),
                    ),
                "12345678912" to
                    PdlPerson(
                        listOf(
                            PdlIdent("12345678912", false, "FOLKEREGISTERIDENT"),
                            PdlIdent("aktorId2", false, "AKTORID"),
                        ),
                    ),
            )
        coEvery { norskHelsenettClient.getByFnr(any(), any()) } returns
            Behandler(
                emptyList(),
                "",
                "HPR",
                null,
                null,
                null,
            )
        coEvery { norskHelsenettClient.getByHpr(any(), any()) } returns
            Behandler(
                emptyList(),
                "",
                "HPR",
                null,
                null,
                null,
            )
        coEvery { syfoSykemeldingRuleClient.executeRuleValidation(any(), any()) } returns
            ValidationResult(
                Status.OK,
                emptyList(),
            )
        coEvery { duplicationService.getDuplicationCheck(any(), any()) } returns null
        coEvery { smtssClient.findBestTssIdEmottak(any(), any(), any(), any()) } returns null
        coEvery { smtssClient.findBestTssInfotrygdId(any(), any(), any(), any()) } returns null
    }

    @Test
    internal fun `Vanlig sykmelding skal gi ok apprec`() {
        every { applicationState.ready } returns true andThen false
        val stringInput = getFileAsString("src/test/resources/fellesformat.xml")
        val textMessage = mockk<TextMessage>(relaxed = true)
        every { textMessage.text } returns stringInput
        every { inputconsumer.receive(1000) } returns textMessage

        runBlocking {
            blockingApplicationRunner.run(inputconsumer, backoutProducer)

            coVerify {
                kafkaproducerApprec.send(match { it.value().apprecStatus == ApprecStatus.OK })
            }
        }
    }

    @Test
    internal fun `Virksomhetsykmelding skal gi ok apprec`() {
        every { applicationState.ready } returns true andThen false
        val stringInput = getFileAsString("src/test/resources/sykmelding_virksomhet.xml")
        val textMessage = mockk<TextMessage>(relaxed = true)
        every { textMessage.text } returns stringInput
        every { inputconsumer.receive(1000) } returns textMessage
        coEvery { norskHelsenettClient.getByHpr(any(), any()) } returns
            Behandler(
                emptyList(),
                "12345678912",
                "HPR",
                null,
                null,
                null,
            )

        runBlocking {
            blockingApplicationRunner.run(inputconsumer, backoutProducer)

            coVerify {
                kafkaproducerApprec.send(
                    match {
                        it.value().apprecStatus == ApprecStatus.OK &&
                            it.value().mottakerOrganisasjon.navn == "Helseforetak 1"
                    },
                )
            }
            coVerify {
                kafkaproducerreceivedSykmelding.send(
                    match {
                        it.value().personNrLege == "12345678912" &&
                            it.value().legeHprNr == "HPR" &&
                            it.value().sykmelding.behandler.fnr == "behandlerfnr" &&
                            it.value().legekontorOrgNr == "123456789"
                    },
                )
            }
        }
    }

    @Test
    internal fun `Sykmelding med regelsettversjon 3 skal gi ok apprec`() {
        every { applicationState.ready } returns true andThen false
        val stringInput = getFileAsString("src/test/resources/sykemelding2013Regelsettversjon3.xml")
        val textMessage = mockk<TextMessage>(relaxed = true)
        every { textMessage.text } returns stringInput
        every { inputconsumer.receive(1000) } returns textMessage
        runBlocking {
            blockingApplicationRunner.run(inputconsumer, backoutProducer)

            coVerify {
                kafkaproducerApprec.send(match { it.value().apprecStatus == ApprecStatus.OK })
            }
        }
    }

    @Test
    internal fun `Sykmelding med melding er ikke byte message eller text message skal gi RuntimeException`() {
        every { applicationState.ready } returns true andThen false
        val textMessage = mockk<TextMessage>(relaxed = true)
        every { textMessage.text } returns null
        every { inputconsumer.receive(1000) } returns textMessage

        runBlocking {
            try {
                blockingApplicationRunner.run(inputconsumer, backoutProducer)
            } catch (exception: Exception) {
                Assertions.assertEquals(
                    "Incoming message needs to be a byte message or text message",
                    exception.message,
                )
            }
        }
    }

    @Test
    internal fun `Sykmelding med gendate frem i tid skal gi avvist apprec`() {
        every { applicationState.ready } returns true andThen false
        val stringInput =
            getFileAsString(
                "src/test/resources/sykemelding2013Regelsettversjon3gendatefremitid.xml"
            )
        val textMessage = mockk<TextMessage>(relaxed = true)
        every { textMessage.text } returns stringInput
        every { inputconsumer.receive(1000) } returns textMessage
        runBlocking {
            blockingApplicationRunner.run(inputconsumer, backoutProducer)

            coVerify {
                kafkaproducerApprec.send(match { it.value().apprecStatus == ApprecStatus.AVVIST })
            }
        }
    }
}
