package no.nav.syfo.application

import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.client.Behandler
import no.nav.syfo.client.EmottakSubscriptionClient
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.client.findBestSamhandlerPraksisEmottak
import no.nav.syfo.client.getHelsepersonellKategori
import no.nav.syfo.handlestatus.handleDuplicateEdiloggid
import no.nav.syfo.handlestatus.handleDuplicateSM2013Content
import no.nav.syfo.handlestatus.handleStatusINVALID
import no.nav.syfo.handlestatus.handleStatusMANUALPROCESSING
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.handlestatus.handleVedleggContainsVirus
import no.nav.syfo.handlestatus.handleVirksomhetssykmeldingOgFnrManglerIHPR
import no.nav.syfo.handlestatus.handleVirksomhetssykmeldingOgHprMangler
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.SYKMELDING_VEDLEGG_COUNTER
import no.nav.syfo.metrics.VIRKSOMHETSYKMELDING
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.OpprettOppgaveKafkaMessage
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.service.VirusScanService
import no.nav.syfo.service.sha256hashstring
import no.nav.syfo.service.updateRedis
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.checkSM2013Content
import no.nav.syfo.util.countNewDiagnoseCode
import no.nav.syfo.util.extractFnrDnrFraBehandler
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.extractHpr
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
import no.nav.syfo.util.removeVedleggFromFellesformat
import no.nav.syfo.util.toString
import no.nav.syfo.util.wrapExceptions
import no.nav.syfo.vedlegg.google.BucketUploadService
import no.nav.syfo.vedlegg.model.BehandlerInfo
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException
import java.io.StringReader
import java.time.ZoneOffset
import java.util.UUID
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.TextMessage

private val sikkerlogg = LoggerFactory.getLogger("securelog")

class BlockingApplicationRunner(
    private val env: Environment,
    private val applicationState: ApplicationState,
    private val emottakSubscriptionClient: EmottakSubscriptionClient,
    private val syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val kuhrSarClient: SarClient,
    private val pdlPersonService: PdlPersonService,
    private val jedis: Jedis,
    private val bucketUploadService: BucketUploadService,
    private val kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
    private val kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
    private val kafkaManuelTaskProducer: KafkaProducer<String, OpprettOppgaveKafkaMessage>,
    private val kafkaproducerApprec: KafkaProducer<String, Apprec>,
    private val kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
    private val virusScanService: VirusScanService
) {

    suspend fun run(
        inputconsumer: MessageConsumer,
        backoutProducer: MessageProducer
    ) {

        wrapExceptions {

            loop@ while (applicationState.ready) {
                val message = inputconsumer.receive(1000)
                var loggingMeta: LoggingMeta? = null
                if (message == null) {
                    delay(100)
                    continue
                }

                try {
                    val inputMessageText = when (message) {
                        is TextMessage -> message.text
                        else -> throw RuntimeException("Incoming message needs to be a byte message or text message")
                    }
                    INCOMING_MESSAGE_COUNTER.inc()
                    val requestLatency = REQUEST_TIME.startTimer()
                    val fellesformat =
                        fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat

                    val vedlegg = getVedlegg(fellesformat)
                    if (vedlegg.isNotEmpty()) {
                        SYKMELDING_VEDLEGG_COUNTER.inc()
                        removeVedleggFromFellesformat(fellesformat)
                    }
                    val fellesformatText = when (vedlegg.isNotEmpty()) {
                        true -> fellesformatMarshaller.toString(fellesformat)
                        false -> inputMessageText
                    }

                    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                    val msgHead = fellesformat.get<XMLMsgHead>()
                    loggingMeta = LoggingMeta(
                        mottakId = receiverBlock.ediLoggId,
                        orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                        msgId = msgHead.msgInfo.msgId
                    )
                    log.info("Received message, {}", StructuredArguments.fields(loggingMeta))

                    val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                    val ediLoggId = receiverBlock.ediLoggId
                    val sha256String = sha256hashstring(healthInformation)
                    val msgId = msgHead.msgInfo.msgId

                    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                    val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
                    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                    val legekontorOrgName = msgHead.msgInfo.sender.organisation.organisationName

                    val originaltPasientFnr = healthInformation.pasient.fodselsnummer.id
                    val erVirksomhetSykmelding = receiverBlock.ebService == "SykmeldingVirksomhet"

                    log.info(
                        "Extracted data, ready to make sync calls to get more data, {}",
                        StructuredArguments.fields(loggingMeta)
                    )
                    val signaturFnr = if (erVirksomhetSykmelding) {
                        log.info("Mottatt virksomhetssykmelding, {}", StructuredArguments.fields(loggingMeta))
                        VIRKSOMHETSYKMELDING.inc()
                        val hpr = extractHpr(fellesformat)?.id
                        if (hpr == null) {
                            handleVirksomhetssykmeldingOgHprMangler(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }
                        val fnr = norskHelsenettClient.getByHpr(hprNummer = hpr, loggingMeta = loggingMeta)?.fnr
                        if (fnr == null) {
                            handleVirksomhetssykmeldingOgFnrManglerIHPR(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        } else {
                            fnr
                        }
                    } else {
                        receiverBlock.avsenderFnrFraDigSignatur
                    }

                    val identer = pdlPersonService.getIdenter(listOf(signaturFnr, originaltPasientFnr), loggingMeta)

                    val samhandlerInfo = kuhrSarClient.getSamhandler(ident = signaturFnr, msgId = msgId)

                    val samhandlerPraksisMatchEmottak = findBestSamhandlerPraksisEmottak(
                        samhandlerInfo,
                        legekontorOrgNr,
                        legekontorHerId,
                        loggingMeta
                    )

                    val samhandlerPraksisTssId = findBestSamhandlerPraksis(
                        samhandlerInfo,
                        legekontorOrgNr,
                        legekontorOrgName,
                        legekontorHerId,
                        loggingMeta
                    )?.samhandlerPraksis?.tss_ident

                    handleEmottakSubscription(
                        samhandlerPraksisMatchEmottak,
                        receiverBlock,
                        emottakSubscriptionClient,
                        msgHead,
                        msgId,
                        loggingMeta
                    )

                    val redisSha256String = jedis.get(sha256String)
                    val redisEdiloggid = jedis.get(ediLoggId)

                    if (redisSha256String != null) {
                        handleDuplicateSM2013Content(
                            redisSha256String, loggingMeta, fellesformat,
                            ediLoggId, msgId, msgHead, env, kafkaproducerApprec
                        )
                        continue@loop
                    } else if (redisEdiloggid != null && redisEdiloggid.length != 21) {
                        log.error(
                            "Redis returned a redisEdiloggid that is longer than 21" +
                                "characters redisEdiloggid: {} {}",
                            redisEdiloggid,
                            StructuredArguments.fields(loggingMeta)
                        )
                        throw RuntimeException("Redis has some issues with geting the redisEdiloggid")
                    } else if (redisEdiloggid != null) {
                        handleDuplicateEdiloggid(
                            redisEdiloggid, loggingMeta, fellesformat,
                            ediLoggId, msgId, msgHead, env, kafkaproducerApprec
                        )
                        continue@loop
                    } else {
                        val pasient = identer[originaltPasientFnr]
                        val behandler = identer[signaturFnr]

                        if (checkSM2013Content(
                                pasient, behandler, healthInformation, originaltPasientFnr, loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                        ) {
                            continue@loop
                        }

                        val signerendeBehandler =
                            norskHelsenettClient.getByFnr(fnr = signaturFnr, loggingMeta = loggingMeta)

                        val behandlenedeBehandler =
                            if (extractFnrDnrFraBehandler(healthInformation) != null || extractHpr(fellesformat)?.id != null) {
                                getBehandlenedeBehandler(
                                    extractHpr(fellesformat)?.id,
                                    extractFnrDnrFraBehandler(healthInformation),
                                    loggingMeta
                                )
                            } else {
                                null
                            }

                        val behandlenedeBehandlerFnr = extractFnrDnrFraBehandler(healthInformation)
                            ?: getBehandlerFnr(
                                avsenderHpr = extractHpr(fellesformat)?.id,
                                signerendeBehandler = signerendeBehandler,
                                behandlenedeBehandler = behandlenedeBehandler
                            ) ?: signaturFnr

                        val behandlenedeBehandlerhprNummer = extractHpr(fellesformat)?.id
                            ?: getBehandlerHprNr(
                                avsenderHpr = extractHpr(fellesformat)?.id,
                                behandlenedeBehandler = behandlenedeBehandler
                            )

                        val sykmelding = healthInformation.toSykmelding(
                            sykmeldingId = UUID.randomUUID().toString(),
                            pasientAktoerId = pasient?.aktorId!!,
                            legeAktoerId = behandler?.aktorId!!,
                            msgId = msgId,
                            signaturDato = getLocalDateTime(msgHead.msgInfo.genDate),
                            behandlerFnr = behandlenedeBehandlerFnr,
                            behandlerHprNr = behandlenedeBehandlerhprNummer
                        )
                        if (originaltPasientFnr != pasient.fnr) {
                            log.info(
                                "Sykmeldingen inneholder eldre ident for pasient, benytter nyeste fra PDL {}",
                                StructuredArguments.fields(loggingMeta)
                            )
                            sikkerlogg.info(
                                "Sykmeldingen inneholder eldre ident for pasient, benytter nyeste fra PDL" +
                                    "originaltPasientFnr: {}, pasientFnr: {}, {}",
                                originaltPasientFnr, pasient.fnr,
                                StructuredArguments.fields(loggingMeta)
                            )
                        }

                        if (vedlegg.isNotEmpty()) {
                            if (virusScanService.vedleggContainsVirus(vedlegg)) {
                                handleVedleggContainsVirus(
                                    loggingMeta, fellesformat, ediLoggId,
                                    msgId, msgHead, env, kafkaproducerApprec,
                                    jedis, sha256String
                                )
                                continue@loop
                            }
                        }

                        val vedleggListe: List<String> = if (vedlegg.isNotEmpty()) {
                            bucketUploadService.lastOppVedlegg(
                                vedlegg = vedlegg,
                                msgId = msgId,
                                personNrPasient = pasient.fnr!!,
                                behandlerInfo = BehandlerInfo(
                                    fornavn = sykmelding.behandler.fornavn,
                                    etternavn = sykmelding.behandler.etternavn,
                                    fnr = signaturFnr
                                ),
                                pasientAktoerId = sykmelding.pasientAktoerId,
                                sykmeldingId = sykmelding.id,
                                loggingMeta = loggingMeta
                            )
                        } else {
                            emptyList()
                        }

                        val receivedSykmelding = ReceivedSykmelding(
                            sykmelding = sykmelding,
                            personNrPasient = pasient.fnr!!,
                            tlfPasient = extractTlfFromKontaktInfo(healthInformation.pasient.kontaktInfo),
                            personNrLege = signaturFnr,
                            navLogId = ediLoggId,
                            msgId = msgId,
                            legeHprNr = signerendeBehandler?.hprNummer,
                            legeHelsepersonellkategori = signerendeBehandler?.godkjenninger?.let {
                                getHelsepersonellKategori(
                                    it
                                )
                            },
                            legekontorOrgNr = legekontorOrgNr,
                            legekontorOrgName = legekontorOrgName,
                            legekontorHerId = legekontorHerId,
                            legekontorReshId = legekontorReshId,
                            mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime()
                                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                            rulesetVersion = healthInformation.regelSettVersjon,
                            fellesformat = fellesformatText,
                            tssid = samhandlerPraksisTssId ?: "",
                            merknader = null,
                            partnerreferanse = receiverBlock.partnerReferanse,
                            vedlegg = vedleggListe,
                            utenlandskSykmelding = null
                        )

                        if (behandlenedeBehandlerFnr != signaturFnr) {
                            logUlikBehandler(loggingMeta)
                        }

                        countNewDiagnoseCode(receivedSykmelding.sykmelding.medisinskVurdering)

                        log.info(
                            "Validating against rules, sykmeldingId {},  {}",
                            StructuredArguments.keyValue("sykmeldingId", sykmelding.id),
                            StructuredArguments.fields(loggingMeta)
                        )
                        val validationResult =
                            syfoSykemeldingRuleClient.executeRuleValidation(receivedSykmelding, loggingMeta)

                        when (validationResult.status) {
                            Status.OK -> handleStatusOK(
                                fellesformat = fellesformat,
                                ediLoggId = ediLoggId,
                                msgId = msgId,
                                msgHead = msgHead,
                                apprecTopic = env.apprecTopic,
                                kafkaproducerApprec = kafkaproducerApprec,
                                loggingMeta = loggingMeta,
                                okSykmeldingTopic = env.okSykmeldingTopic,
                                receivedSykmelding = receivedSykmelding,
                                kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmelding
                            )

                            Status.MANUAL_PROCESSING -> handleStatusMANUALPROCESSING(
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
                                kafkaproducervalidationResult = kafkaproducervalidationResult,
                                behandlingsUtfallTopic = env.behandlingsUtfallTopic,
                                kafkaproducerManuellOppgave = kafkaproducerManuellOppgave,
                                syfoSmManuellTopic = env.syfoSmManuellTopic,
                                produserOppgaveTopic = env.produserOppgaveTopic
                            )

                            Status.INVALID -> handleStatusINVALID(
                                validationResult = validationResult,
                                kafkaproducerreceivedSykmelding = kafkaproducerreceivedSykmelding,
                                kafkaproducervalidationResult = kafkaproducervalidationResult,
                                avvistSykmeldingTopic = env.avvistSykmeldingTopic,
                                receivedSykmelding = receivedSykmelding,
                                loggingMeta = loggingMeta,
                                fellesformat = fellesformat,
                                apprecTopic = env.apprecTopic,
                                behandlingsUtfallTopic = env.behandlingsUtfallTopic,
                                kafkaproducerApprec = kafkaproducerApprec,
                                ediLoggId = ediLoggId,
                                msgId = msgId,
                                msgHead = msgHead
                            )
                        }

                        val currentRequestLatency = requestLatency.observeDuration()

                        updateRedis(jedis, ediLoggId, sha256String)
                        log.info(
                            "Message got outcome {}, {}, processing took {}s, {}",
                            StructuredArguments.keyValue("status", validationResult.status),
                            StructuredArguments.keyValue(
                                "ruleHits",
                                validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }
                            ),
                            StructuredArguments.keyValue("latency", currentRequestLatency),
                            StructuredArguments.fields(loggingMeta)
                        )
                    }
                } catch (jedisException: JedisConnectionException) {
                    log.error(
                        "Exception caught, redis issue while handling message, sending to backout ${
                        StructuredArguments.fields(
                            loggingMeta
                        )
                        }",
                        jedisException
                    )
                    backoutProducer.send(message)
                    log.error("Setting applicationState.alive to false")
                    applicationState.alive = false
                } catch (e: Exception) {
                    log.error(
                        "Exception caught while handling message, sending to backout ${
                        StructuredArguments.fields(
                            loggingMeta
                        )
                        }",
                        e
                    )
                    backoutProducer.send(message)
                } finally {
                    message.acknowledge()
                }
            }
        }
    }

    private suspend fun getBehandlenedeBehandler(
        behandlenedeBehandlerHpr: String?,
        behandlenedeBehandlerfnr: String?,
        loggingMeta: LoggingMeta
    ): Behandler? {
        return if (behandlenedeBehandlerHpr != null) {
            norskHelsenettClient.getByHpr(behandlenedeBehandlerHpr, loggingMeta)
        } else if (behandlenedeBehandlerfnr != null) {
            norskHelsenettClient.getByFnr(behandlenedeBehandlerfnr, loggingMeta)
        } else {
            null
        }
    }

    /**
     * Finds the sender's FNR, either by matching HPR against signerende behandler or by doing a lookup against HPR
     */
    private fun getBehandlerFnr(
        avsenderHpr: String?,
        signerendeBehandler: Behandler?,
        behandlenedeBehandler: Behandler?
    ): String? {
        return when (avsenderHpr) {
            null -> {
                null
            }

            signerendeBehandler?.hprNummer -> {
                signerendeBehandler.fnr
            }

            else -> {
                behandlenedeBehandler?.fnr
            }
        }
    }

    private fun getBehandlerHprNr(
        avsenderHpr: String?,
        behandlenedeBehandler: Behandler?
    ): String? {
        return avsenderHpr ?: behandlenedeBehandler?.hprNummer
    }
}
