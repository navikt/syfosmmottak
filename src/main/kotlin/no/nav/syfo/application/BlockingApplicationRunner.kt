package no.nav.syfo.application

import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import jakarta.jms.Message
import jakarta.jms.MessageConsumer
import jakarta.jms.MessageProducer
import jakarta.jms.TextMessage
import java.io.StringReader
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetTime
import java.time.ZoneOffset
import java.util.*
import javax.xml.parsers.SAXParserFactory
import javax.xml.transform.Source
import javax.xml.transform.sax.SAXSource
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.syfo.ApplicationState
import no.nav.syfo.EnvironmentVariables
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SmtssClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.client.getHelsepersonellKategori
import no.nav.syfo.duplicationcheck.model.Duplicate
import no.nav.syfo.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.handlestatus.handleDuplicateSM2013Content
import no.nav.syfo.handlestatus.handleSignaturDatoInTheFuture
import no.nav.syfo.handlestatus.handleStatusINVALID
import no.nav.syfo.handlestatus.handleStatusMANUALPROCESSING
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.handlestatus.handleVedleggContainsVirus
import no.nav.syfo.handlestatus.handleVedleggOver300MB
import no.nav.syfo.handlestatus.handleVirksomhetssykmeldingOgFnrManglerIHPR
import no.nav.syfo.handlestatus.handleVirksomhetssykmeldingOgHprMangler
import no.nav.syfo.logger
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.INCOMING_MESSAGE_DELAY
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.SYKMELDING_MISSNG_ORG_NUMBER_COUNTER
import no.nav.syfo.metrics.SYKMELDING_VEDLEGG_COUNTER
import no.nav.syfo.metrics.VIRKSOMHETSYKMELDING
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.ReceivedSykmeldingWithValidation
import no.nav.syfo.model.Status
import no.nav.syfo.model.toAvsenderSystem
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.pdl.model.PdlPerson
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.service.DuplicationService
import no.nav.syfo.service.UploadSykmeldingService
import no.nav.syfo.service.VirusScanService
import no.nav.syfo.service.fileSizeLagerThan300MegaBytes
import no.nav.syfo.service.sha256hashstring
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.checkSM2013Content
import no.nav.syfo.util.extractFnrDnrFraBehandler
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.extractHprBehandler
import no.nav.syfo.util.extractHprOrganization
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.util.extractTlfFromKontaktInfo
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.get
import no.nav.syfo.util.getLocalDateTime
import no.nav.syfo.util.getVedlegg
import no.nav.syfo.util.handleEmottakSubscription
import no.nav.syfo.util.logUlikBehandler
import no.nav.syfo.util.padHpr
import no.nav.syfo.util.removeVedleggFromFellesformat
import no.nav.syfo.util.toString
import no.nav.syfo.util.wrapExceptions
import no.nav.syfo.vedlegg.google.BucketUploadService
import no.nav.syfo.vedlegg.model.BehandlerInfo
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import org.xml.sax.InputSource

val sikkerlogg = LoggerFactory.getLogger("securelog")

class BlockingApplicationRunner(
    private val env: EnvironmentVariables,
    private val applicationState: ApplicationState,
    private val emottakSubscriptionClient: EmottakSubscriptionClient,
    private val syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val pdlPersonService: PdlPersonService,
    private val bucketUploadService: BucketUploadService,
    private val kafkaproducerreceivedSykmelding:
        KafkaProducer<String, ReceivedSykmeldingWithValidation>,
    private val kafkaManuelTaskProducer: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    private val kafkaproducerApprec: KafkaProducer<String, Apprec>,
    private val kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    private val virusScanService: VirusScanService,
    private val duplicationService: DuplicationService,
    private val smtssClient: SmtssClient,
    private val inputconsumer: MessageConsumer,
    private val backoutProducer: MessageProducer,
    private val UploadSykmeldingService: UploadSykmeldingService,
) {

    suspend fun run() {
        wrapExceptions {
            loop@ while (applicationState.ready) {
                val message = inputconsumer.receive(1000)
                if (message == null) {
                    delay(100)
                    continue
                }
                val messageTimestamp =
                    OffsetTime.ofInstant(Instant.ofEpochMilli(message.jmsTimestamp), ZoneOffset.UTC)

                logger.info("Received message with timestamp {}", messageTimestamp)
                processMqMessage(message)
            }
        }
    }

    @WithSpan
    private suspend fun processMqMessage(message: Message) {
        var loggingMeta: LoggingMeta? = null
        try {
            val inputMessageText =
                when (message) {
                    is TextMessage -> message.text
                    else ->
                        throw RuntimeException(
                            "Incoming message needs to be a byte message or text message",
                        )
                }
            INCOMING_MESSAGE_COUNTER.inc()
            val now = Instant.now().toEpochMilli()
            val delay = now - message.jmsTimestamp
            INCOMING_MESSAGE_DELAY.observe(delay / 1000.0)

            val requestLatency = REQUEST_TIME.startTimer()
            val fellesformat = safeUnmarshal(inputMessageText)

            val vedlegg = getVedlegg(fellesformat)
            if (vedlegg.isNotEmpty()) {
                SYKMELDING_VEDLEGG_COUNTER.inc()
                removeVedleggFromFellesformat(fellesformat)
            }
            val fellesformatText =
                when (vedlegg.isNotEmpty()) {
                    true -> fellesformatMarshaller.toString(fellesformat)
                    false -> inputMessageText
                }
            val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
            val msgHead = fellesformat.get<XMLMsgHead>()
            val legekontorOrgNr =
                extractOrganisationNumberFromSender(fellesformat)?.id?.replace(" ", "")?.trim()
            loggingMeta =
                LoggingMeta(
                    mottakId = receiverBlock.ediLoggId,
                    orgNr = legekontorOrgNr,
                    msgId = msgHead.msgInfo.msgId,
                )
            logger.info("Received message, {}", StructuredArguments.fields(loggingMeta))

            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val ediLoggId = receiverBlock.ediLoggId
            val sha256String = sha256hashstring(healthInformation)
            val msgId = msgHead.msgInfo.msgId

            val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
            val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
            val legekontorOrgName =
                msgHead.msgInfo.sender.organisation.organisationName.replace(
                    "[^\\p{ASCII}]".toRegex(),
                    "",
                )

            val partnerReferanse = receiverBlock.partnerReferanse

            val originaltPasientFnr = healthInformation.pasient.fodselsnummer.id
            val erVirksomhetSykmelding = receiverBlock.ebService == "SykmeldingVirksomhet"

            val mottatDato =
                receiverBlock.mottattDatotid
                    .toGregorianCalendar()
                    .toZonedDateTime()
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime()

            val avsenderSystem = healthInformation.avsenderSystem.toAvsenderSystem()

            val sykmeldingId = UUID.randomUUID().toString()
            val rulesetVersion = healthInformation.regelSettVersjon

            // Enrich current span with sykmelding id
            val currentSpan = Span.current()
            currentSpan.setAttribute("sykmeldingId", sykmeldingId)

            val duplicateCheck =
                DuplicateCheck(
                    sykmeldingId,
                    sha256String,
                    ediLoggId,
                    msgId,
                    mottatDato,
                    avsenderSystem.navn,
                    avsenderSystem.versjon,
                    legekontorOrgNr,
                    rulesetVersion,
                )

            logger.info(
                "Extracted data, ready to make sync calls to get more data, {}",
                StructuredArguments.fields(loggingMeta),
            )

            sikkerlogg.info(
                "fellesformat: $fellesformatText",
                StructuredArguments.fields(loggingMeta),
            )

            if (legekontorOrgNr == null) {
                SYKMELDING_MISSNG_ORG_NUMBER_COUNTER.inc()
                logger.info(
                    "Missing org number, from epj ${avsenderSystem.navn} {}",
                    StructuredArguments.fields(loggingMeta),
                )
            }

            val (signerendeBehandler: Behandler?, identer: Map<String, PdlPerson?>) =
                if (erVirksomhetSykmelding) {
                    logger.info(
                        "Mottatt virksomhetssykmelding, {}",
                        StructuredArguments.fields(loggingMeta),
                    )
                    VIRKSOMHETSYKMELDING.inc()
                    val hpr = extractHprOrganization(fellesformat)
                    if (hpr == null) {
                        handleVirksomhetssykmeldingOgHprMangler(
                            loggingMeta,
                            fellesformat,
                            ediLoggId,
                            msgId,
                            msgHead,
                            env,
                            kafkaproducerApprec,
                            duplicationService,
                            duplicateCheck,
                        )
                        return
                    }

                    val formatedHpr = padHpr(hpr.trim())!!

                    val behandler =
                        norskHelsenettClient.getByHpr(
                            hprNummer = formatedHpr,
                            loggingMeta = loggingMeta
                        )
                    if (behandler?.fnr == null) {
                        handleVirksomhetssykmeldingOgFnrManglerIHPR(
                            loggingMeta,
                            fellesformat,
                            ediLoggId,
                            msgId,
                            msgHead,
                            env,
                            kafkaproducerApprec,
                            duplicationService,
                            duplicateCheck,
                        )
                        return
                    } else {
                        behandler to
                            getIdenter(listOf(behandler.fnr, originaltPasientFnr), loggingMeta)
                    }
                } else {
                    val identer =
                        getIdenter(
                            listOf(receiverBlock.avsenderFnrFraDigSignatur, originaltPasientFnr),
                            loggingMeta
                        )
                    norskHelsenettClient
                        .getByFnr(
                            identer[receiverBlock.avsenderFnrFraDigSignatur]?.folkereigsterIdenter
                                ?: emptyList(),
                            loggingMeta = loggingMeta
                        )
                        ?.copy(fnr = receiverBlock.avsenderFnrFraDigSignatur) to identer
                }

            requireNotNull(signerendeBehandler) {
                "Signerende behandler er null, should not happen! $loggingMeta"
            }

            requireNotNull(signerendeBehandler.fnr) {
                "Signernede behandler fnr is null, should not happen! $loggingMeta"
            }

            requireNotNull(signerendeBehandler.fnr) {
                "Signernede behandler fnr is null, should not happen! $loggingMeta"
            }

            val tssIdEmottak =
                smtssClient.findBestTssIdEmottak(
                    signerendeBehandler.fnr,
                    legekontorOrgName,
                    loggingMeta,
                    sykmeldingId,
                )

            val tssIdInfotrygd =
                if (!tssIdEmottak.isNullOrEmpty()) {
                    tssIdEmottak
                } else {
                    smtssClient.findBestTssInfotrygdId(
                        signerendeBehandler.fnr,
                        legekontorOrgName,
                        loggingMeta,
                        sykmeldingId,
                    )
                }

            logger.info(
                "tssIdEmottak is $tssIdEmottak {}",
                StructuredArguments.fields(loggingMeta),
            )
            logger.info(
                "tssIdInfotrygd is $tssIdInfotrygd {}",
                StructuredArguments.fields(loggingMeta),
            )

            handleEmottakSubscription(
                tssIdEmottak,
                emottakSubscriptionClient,
                msgHead,
                msgId,
                partnerReferanse,
                loggingMeta,
            )

            val duplicationCheckSha256String =
                duplicationService.getDuplicationCheck(sha256String, ediLoggId)

            if (duplicationCheckSha256String != null) {
                val duplicate =
                    Duplicate(
                        sykmeldingId,
                        ediLoggId,
                        msgId,
                        duplicationCheckSha256String.sykmeldingId,
                        mottatDato,
                        avsenderSystem.navn,
                        avsenderSystem.versjon,
                        legekontorOrgNr,
                    )

                handleDuplicateSM2013Content(
                    duplicationCheckSha256String.mottakId,
                    loggingMeta,
                    fellesformat,
                    ediLoggId,
                    msgId,
                    msgHead,
                    env,
                    kafkaproducerApprec,
                    duplicationService,
                    duplicate,
                )
                return
            } else {
                val pasient = identer[originaltPasientFnr]
                val signerendeFnr = signerendeBehandler.fnr
                val signerendeAktorId = identer[signerendeBehandler.fnr]?.aktorId

                requireNotNull(pasient?.fnr) {
                    "Pasient not in PDL, should not happen! $loggingMeta"
                }
                requireNotNull(pasient.aktorId) {
                    "Pasient not in PDL, should not happen! $loggingMeta"
                }
                requireNotNull(signerendeFnr) {
                    "Signerende behandler not in PDL, should not happen! $loggingMeta"
                }
                requireNotNull(signerendeAktorId) {
                    "Signerende behandler not in PDL, should not happen! $loggingMeta"
                }
                if (
                    checkSM2013Content(
                        pasient,
                        signerendeAktorId = signerendeAktorId,
                        healthInformation,
                        originaltPasientFnr,
                        loggingMeta,
                        fellesformat,
                        ediLoggId,
                        msgId,
                        msgHead,
                        env,
                        kafkaproducerApprec,
                        duplicationService,
                        duplicateCheck,
                    )
                ) {
                    return
                }

                val behandlenedeBehandler =
                    getBehandlenedeBehandler(healthInformation, fellesformat, loggingMeta)

                val sykmelding =
                    healthInformation.toSykmelding(
                        sykmeldingId = sykmeldingId,
                        pasientAktoerId = pasient.aktorId,
                        legeAktoerId = signerendeAktorId,
                        msgId = msgId,
                        signaturDato = getLocalDateTime(msgHead.msgInfo.genDate),
                        behandlerFnr = behandlenedeBehandler?.fnr ?: signerendeBehandler.fnr,
                        behandlerHprNr = behandlenedeBehandler?.hprNummer
                                ?: signerendeBehandler.hprNummer,
                    )
                if (originaltPasientFnr != pasient.fnr) {
                    logger.info(
                        "Sykmeldingen inneholder eldre ident for pasient, benytter nyeste fra PDL {}",
                        StructuredArguments.fields(loggingMeta),
                    )
                    sikkerlogg.info(
                        "Sykmeldingen inneholder eldre ident for pasient, benytter nyeste fra PDL" +
                            "originaltPasientFnr: {}, pasientFnr: {}, {}",
                        originaltPasientFnr,
                        pasient.fnr,
                        StructuredArguments.fields(loggingMeta),
                    )
                }

                if (vedlegg.isNotEmpty()) {
                    val vedleggOver300MegaByte =
                        vedlegg.filter {
                            fileSizeLagerThan300MegaBytes(
                                Base64.getMimeDecoder().decode(it.content.content),
                            )
                        }

                    if (vedleggOver300MegaByte.isNotEmpty()) {
                        handleVedleggOver300MB(
                            loggingMeta,
                            fellesformat,
                            ediLoggId,
                            msgId,
                            msgHead,
                            env,
                            kafkaproducerApprec,
                            duplicationService,
                            duplicateCheck,
                        )
                        return
                    }

                    if (virusScanService.vedleggContainsVirus(vedlegg)) {
                        handleVedleggContainsVirus(
                            loggingMeta,
                            fellesformat,
                            ediLoggId,
                            msgId,
                            msgHead,
                            env,
                            kafkaproducerApprec,
                            duplicationService,
                            duplicateCheck,
                        )
                        return
                    }
                }

                if (sykmelding.signaturDato.isAfter(LocalDateTime.now())) {
                    handleSignaturDatoInTheFuture(
                        loggingMeta,
                        fellesformat,
                        ediLoggId,
                        msgId,
                        msgHead,
                        env,
                        kafkaproducerApprec,
                        duplicationService,
                        duplicateCheck,
                    )
                    return
                }

                val vedleggListe: List<String> =
                    if (vedlegg.isNotEmpty()) {
                        bucketUploadService.lastOppVedlegg(
                            vedlegg = vedlegg,
                            msgId = msgId,
                            personNrPasient = pasient.fnr,
                            behandlerInfo =
                                BehandlerInfo(
                                    fornavn = sykmelding.behandler.fornavn,
                                    etternavn = sykmelding.behandler.etternavn,
                                    fnr = behandlenedeBehandler?.fnr,
                                ),
                            pasientAktoerId = sykmelding.pasientAktoerId,
                            sykmeldingId = sykmelding.id,
                            loggingMeta = loggingMeta,
                        )
                    } else {
                        emptyList()
                    }

                val receivedSykmelding =
                    ReceivedSykmelding(
                        sykmelding = sykmelding,
                        personNrPasient = pasient.fnr,
                        tlfPasient =
                            extractTlfFromKontaktInfo(
                                healthInformation.pasient.kontaktInfo,
                            ),
                        personNrLege = signerendeBehandler.fnr,
                        navLogId = ediLoggId,
                        msgId = msgId,
                        legeHprNr = signerendeBehandler.hprNummer,
                        legeHelsepersonellkategori =
                            getHelsepersonellKategori(
                                signerendeBehandler.godkjenninger,
                            ),
                        legekontorOrgNr = legekontorOrgNr,
                        legekontorOrgName = legekontorOrgName,
                        legekontorHerId = legekontorHerId,
                        legekontorReshId = legekontorReshId,
                        mottattDato = mottatDato,
                        rulesetVersion = rulesetVersion,
                        fellesformat = fellesformatText,
                        tssid = tssIdInfotrygd ?: "",
                        merknader = null,
                        partnerreferanse = partnerReferanse,
                        vedlegg = vedleggListe,
                        utenlandskSykmelding = null,
                    )

                if (behandlenedeBehandler?.fnr != signerendeBehandler.fnr) {
                    logUlikBehandler(loggingMeta)
                }

                logger.info(
                    "Validating against rules, sykmeldingId {},  {}",
                    StructuredArguments.keyValue("sykmeldingId", sykmelding.id),
                    StructuredArguments.fields(loggingMeta),
                )
                val validationResult =
                    syfoSykemeldingRuleClient.executeRuleValidation(
                        receivedSykmelding,
                        loggingMeta,
                    )

                when (validationResult.status) {
                    Status.OK ->
                        handleStatusOK(
                            fellesformat = fellesformat,
                            ediLoggId = ediLoggId,
                            msgId = msgId,
                            msgHead = msgHead,
                            apprecTopic = env.apprecTopic,
                            kafkaproducerApprec = kafkaproducerApprec,
                            loggingMeta = loggingMeta,
                            okSykmeldingTopic = env.okSykmeldingTopic,
                            receivedSykmelding = receivedSykmelding,
                            kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmelding,
                        )
                    Status.MANUAL_PROCESSING ->
                        handleStatusMANUALPROCESSING(
                            receivedSykmelding = receivedSykmelding,
                            loggingMeta = loggingMeta,
                            fellesformat = fellesformat,
                            ediLoggId = ediLoggId,
                            msgId = msgId,
                            msgHead = msgHead,
                            apprecTopic = env.apprecTopic,
                            kafkaproducerApprec = kafkaproducerApprec,
                            validationResult = validationResult,
                            kafkaManuelTaskProducer = kafkaManuelTaskProducer,
                            kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmelding,
                            manuellBehandlingSykmeldingTopic = env.manuellBehandlingSykmeldingTopic,
                            kafkaproducerManuellOppgave = kafkaproducerManuellOppgave,
                            syfoSmManuellTopic = env.syfoSmManuellTopic,
                            produserOppgaveTopic = env.produserOppgaveTopic,
                        )
                    Status.INVALID ->
                        handleStatusINVALID(
                            validationResult = validationResult,
                            kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmelding,
                            avvistSykmeldingTopic = env.avvistSykmeldingTopic,
                            receivedSykmelding = receivedSykmelding,
                            loggingMeta = loggingMeta,
                            fellesformat = fellesformat,
                            apprecTopic = env.apprecTopic,
                            kafkaproducerApprec = kafkaproducerApprec,
                            ediLoggId = ediLoggId,
                            msgId = msgId,
                            msgHead = msgHead,
                        )
                }

                val currentRequestLatency = requestLatency.observeDuration()
                UploadSykmeldingService.uploadOriginalMessage(sykmeldingId, inputMessageText)
                duplicationService.persistDuplicationCheck(duplicateCheck)

                logger.info(
                    "Message got outcome {}, {}, processing took {}s, {}, {}",
                    StructuredArguments.keyValue("status", validationResult.status),
                    StructuredArguments.keyValue(
                        "ruleHits",
                        validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName },
                    ),
                    StructuredArguments.keyValue("latency", currentRequestLatency),
                    StructuredArguments.fields(loggingMeta),
                    StructuredArguments.keyValue("sykmeldingId", sykmeldingId),
                )
            }
        } catch (e: Exception) {
            logger.error(
                "Exception caught while handling message, sending to backout ${
                    StructuredArguments.fields(
                        loggingMeta,
                    )
                }",
                e,
            )
            backoutProducer.send(message)
        } finally {
            message.acknowledge()
        }
    }

    private suspend fun getIdenter(
        identer: List<String>,
        loggingMeta: LoggingMeta
    ): Map<String, PdlPerson?> =
        pdlPersonService.getIdenter(
            identer,
            loggingMeta,
        )

    private suspend fun getBehandlenedeBehandler(
        healthInformation: HelseOpplysningerArbeidsuforhet,
        fellesformat: XMLEIFellesformat,
        loggingMeta: LoggingMeta,
    ): Behandler? {

        val behandlerHpr =
            padHpr(extractHprBehandler(healthInformation))?.let {
                norskHelsenettClient.getByHpr(it, loggingMeta)
            }
        if (behandlerHpr != null) {
            return behandlerHpr
        }
        val behandlerFromFnr =
            extractFnrDnrFraBehandler(healthInformation)?.let {
                norskHelsenettClient.getByFnr(listOf(it), loggingMeta)
            }
        if (behandlerFromFnr != null) {
            return behandlerFromFnr
        }

        return extractHprOrganization(fellesformat)?.let {
            norskHelsenettClient.getByHpr(it, loggingMeta)
        }
    }

    private fun safeUnmarshal(inputMessageText: String): XMLEIFellesformat {
        // Disable XXE
        val spf: SAXParserFactory = SAXParserFactory.newInstance()
        spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        spf.isNamespaceAware = true

        val xmlSource: Source =
            SAXSource(
                spf.newSAXParser().xmlReader,
                InputSource(StringReader(inputMessageText)),
            )
        return fellesformatUnmarshaller.unmarshal(xmlSource) as XMLEIFellesformat
    }
}
