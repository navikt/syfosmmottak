package no.nav.syfo.application

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.client.Godkjenning
import no.nav.syfo.client.NorskHelsenettClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.handlestatus.handleAktivitetOrPeriodeIsMissing
import no.nav.syfo.handlestatus.handleAnnenFraversArsakkodeVIsmissing
import no.nav.syfo.handlestatus.handleArbeidsplassenArsakskodeHarUgyldigVerdi
import no.nav.syfo.handlestatus.handleArbeidsplassenArsakskodeIsmissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeBeskrivelseMissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeIsMissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeVerkIsMissing
import no.nav.syfo.handlestatus.handleDoctorNotFoundInPDL
import no.nav.syfo.handlestatus.handleDuplicateEdiloggid
import no.nav.syfo.handlestatus.handleDuplicateSM2013Content
import no.nav.syfo.handlestatus.handleFnrAndDnrAndHprIsmissingFromBehandler
import no.nav.syfo.handlestatus.handleHouvedDiagnoseDiagnoseBeskrivelseMissing
import no.nav.syfo.handlestatus.handleHouvedDiagnoseDiagnosekodeMissing
import no.nav.syfo.handlestatus.handleMedisinskeArsakskodeHarUgyldigVerdi
import no.nav.syfo.handlestatus.handleMedisinskeArsakskodeIsmissing
import no.nav.syfo.handlestatus.handlePatientNotFoundInPDL
import no.nav.syfo.handlestatus.handleStatusINVALID
import no.nav.syfo.handlestatus.handleStatusMANUALPROCESSING
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.handlestatus.handleTestFnrInProd
import no.nav.syfo.kafka.vedlegg.producer.KafkaVedleggProducer
import no.nav.syfo.log
import no.nav.syfo.metrics.IKKE_OPPDATERT_PARTNERREG
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.MANGLER_TSSIDENT
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.SYKMELDING_VEDLEGG_COUNTER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.service.samhandlerParksisisLegevakt
import no.nav.syfo.service.sha256hashstring
import no.nav.syfo.service.startSubscription
import no.nav.syfo.service.updateRedis
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.annenFraversArsakkodeVMangler
import no.nav.syfo.util.arbeidsplassenArsakskodeHarUgyldigVerdi
import no.nav.syfo.util.arbeidsplassenArsakskodeMangler
import no.nav.syfo.util.countNewDiagnoseCode
import no.nav.syfo.util.erTestFnr
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.extractHpr
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.util.fellesformatMarshaller
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.fnrOgDnrMangler
import no.nav.syfo.util.get
import no.nav.syfo.util.getFnrOrDnr
import no.nav.syfo.util.getLocalDateTime
import no.nav.syfo.util.getVedlegg
import no.nav.syfo.util.hprMangler
import no.nav.syfo.util.logUlikBehandler
import no.nav.syfo.util.medisinskeArsakskodeHarUgyldigVerdi
import no.nav.syfo.util.medisinskeArsakskodeMangler
import no.nav.syfo.util.removeVedleggFromFellesformat
import no.nav.syfo.util.toString
import no.nav.syfo.util.wrapExceptions
import org.apache.kafka.clients.producer.KafkaProducer
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException
import java.io.StringReader
import java.time.ZoneOffset
import java.util.UUID
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage

class BlockingApplicationRunner(
    private val env: Environment,
    private val applicationState: ApplicationState,
    private val subscriptionEmottak: SubscriptionPort,
    private val syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
    private val norskHelsenettClient: NorskHelsenettClient,
    private val kuhrSarClient: SarClient,
    private val pdlPersonService: PdlPersonService,
    private val jedis: Jedis,
    private val session: Session
) {

    @KtorExperimentalAPI
    suspend fun run(
        inputconsumer: MessageConsumer,
        syfoserviceProducer: MessageProducer,
        backoutProducer: MessageProducer,
        kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
        kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
        kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>,
        kafkaproducerApprec: KafkaProducer<String, Apprec>,
        kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
        kafkaVedleggProducer: KafkaVedleggProducer
    ) {

        wrapExceptions {

            loop@ while (applicationState.ready) {
                val message = inputconsumer.receiveNoWait()
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

                    val pasientFnr = healthInformation.pasient.fodselsnummer.id
                    val signaturFnr = receiverBlock.avsenderFnrFraDigSignatur
                    val behandlerFnr = getFnrOrDnr(healthInformation)

                    val behandler = norskHelsenettClient.getBehandler(fnr = signaturFnr, loggingMeta = loggingMeta)

                    val identer = pdlPersonService.getAktorids(listOf(signaturFnr, pasientFnr), loggingMeta)

                    val samhandlerInfo = kuhrSarClient.getSamhandler(signaturFnr)
                    val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                        samhandlerInfo,
                        legekontorOrgName,
                        legekontorHerId,
                        loggingMeta
                    )
                    val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis
                    if (samhandlerPraksis?.tss_ident == null) {
                        MANGLER_TSSIDENT.inc()
                    }
                    if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
                        log.info(
                            "SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created, {}",
                            StructuredArguments.fields(loggingMeta)
                        )
                        IKKE_OPPDATERT_PARTNERREG.inc()
                    } else {
                        when (samhandlerPraksis) {
                            null -> {
                                log.info("SamhandlerPraksis is Not found, {}", StructuredArguments.fields(loggingMeta))
                                IKKE_OPPDATERT_PARTNERREG.inc()
                            }
                            else -> if (!samhandlerParksisisLegevakt(samhandlerPraksis) &&
                                !receiverBlock.partnerReferanse.isNullOrEmpty() &&
                                receiverBlock.partnerReferanse.isNotBlank()
                            ) {
                                startSubscription(
                                    subscriptionEmottak,
                                    samhandlerPraksis,
                                    msgHead,
                                    receiverBlock,
                                    loggingMeta
                                )
                            } else {
                                log.info(
                                    "SamhandlerPraksis is Legevakt or partnerReferanse is empty or blank, subscription_emottak is not created, {}",
                                    StructuredArguments.fields(loggingMeta)
                                )
                                IKKE_OPPDATERT_PARTNERREG.inc()
                            }
                        }
                    }

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
                        val patientAktorId = identer[pasientFnr]
                        val doctorAktorId = identer[signaturFnr]

                        if (patientAktorId == null) {
                            handlePatientNotFoundInPDL(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }
                        if (doctorAktorId == null) {
                            handleDoctorNotFoundInPDL(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (healthInformation.aktivitet == null || healthInformation.aktivitet.periode.isNullOrEmpty()) {
                            handleAktivitetOrPeriodeIsMissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.biDiagnoser != null &&
                            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.v.isNullOrEmpty() }
                        ) {
                            handleBiDiagnoserDiagnosekodeIsMissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.biDiagnoser != null &&
                            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.s.isNullOrEmpty() }
                        ) {
                            handleBiDiagnoserDiagnosekodeVerkIsMissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.biDiagnoser != null &&
                            healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.dn.isNullOrEmpty() }
                        ) {
                            handleBiDiagnoserDiagnosekodeBeskrivelseMissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (fnrOgDnrMangler(healthInformation) && hprMangler(healthInformation)) {
                            handleFnrAndDnrAndHprIsmissingFromBehandler(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.hovedDiagnose?.diagnosekode != null &&
                            healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v == null
                        ) {
                            handleHouvedDiagnoseDiagnosekodeMissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.hovedDiagnose?.diagnosekode != null &&
                            healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.dn == null
                        ) {
                            handleHouvedDiagnoseDiagnoseBeskrivelseMissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (medisinskeArsakskodeMangler(healthInformation)) {
                            handleMedisinskeArsakskodeIsmissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (medisinskeArsakskodeHarUgyldigVerdi(healthInformation)) {
                            handleMedisinskeArsakskodeHarUgyldigVerdi(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (arbeidsplassenArsakskodeMangler(healthInformation)) {
                            handleArbeidsplassenArsakskodeIsmissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (arbeidsplassenArsakskodeHarUgyldigVerdi(healthInformation)) {
                            handleArbeidsplassenArsakskodeHarUgyldigVerdi(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (erTestFnr(pasientFnr) && env.cluster == "prod-fss") {
                            handleTestFnrInProd(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        if (annenFraversArsakkodeVMangler(healthInformation)) {
                            handleAnnenFraversArsakkodeVIsmissing(
                                loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String
                            )
                            continue@loop
                        }

                        val sykmelding = healthInformation.toSykmelding(
                            sykmeldingId = UUID.randomUUID().toString(),
                            pasientAktoerId = patientAktorId,
                            legeAktoerId = doctorAktorId,
                            msgId = msgId,
                            signaturDato = getLocalDateTime(msgHead.msgInfo.genDate),
                            hprFnrBehandler = signaturFnr
                        )
                        val receivedSykmelding = ReceivedSykmelding(
                            sykmelding = sykmelding,
                            personNrPasient = pasientFnr,
                            tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                            personNrLege = signaturFnr,
                            navLogId = ediLoggId,
                            msgId = msgId,
                            legeHprNr = behandler?.hprNummer,
                            legeHelsepersonellkategori = behandler?.godkjenninger?.let {
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
                            tssid = samhandlerPraksis?.tss_ident ?: "",
                            merknader = null,
                            partnerreferanse = receiverBlock.partnerReferanse
                        )

                        when (behandlerFnr) {
                            null -> if (behandler?.hprNummer != extractHpr(fellesformat)?.id) {
                                logUlikBehandler(loggingMeta)
                            }
                            else -> if (behandlerFnr != signaturFnr) {
                                logUlikBehandler(loggingMeta)
                            }
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
                                fellesformat,
                                ediLoggId,
                                msgId,
                                msgHead,
                                env.sm2013Apprec,
                                kafkaproducerApprec,
                                loggingMeta,
                                session,
                                syfoserviceProducer,
                                healthInformation,
                                env.syfoserviceQueueName,
                                env.sm2013AutomaticHandlingTopic,
                                receivedSykmelding,
                                kafkaproducerreceivedSykmelding
                            )
                            Status.MANUAL_PROCESSING -> handleStatusMANUALPROCESSING(
                                receivedSykmelding,
                                loggingMeta,
                                fellesformat,
                                ediLoggId,
                                msgId,
                                msgHead,
                                env.sm2013Apprec,
                                kafkaproducerApprec,
                                session,
                                syfoserviceProducer,
                                healthInformation,
                                env.syfoserviceQueueName,
                                validationResult,
                                kafkaManuelTaskProducer,
                                kafkaproducerreceivedSykmelding,
                                env.sm2013ManualHandlingTopic,
                                kafkaproducervalidationResult,
                                env.sm2013BehandlingsUtfallTopic,
                                kafkaproducerManuellOppgave,
                                env.syfoSmManuellTopic
                            )

                            Status.INVALID -> handleStatusINVALID(
                                validationResult,
                                kafkaproducerreceivedSykmelding,
                                kafkaproducervalidationResult,
                                env.sm2013InvalidHandlingTopic,
                                receivedSykmelding,
                                loggingMeta,
                                fellesformat,
                                env.sm2013Apprec,
                                env.sm2013BehandlingsUtfallTopic,
                                kafkaproducerApprec,
                                ediLoggId,
                                msgId,
                                msgHead
                            )
                        }

                        if (vedlegg.isNotEmpty()) {
                            kafkaVedleggProducer.sendVedlegg(vedlegg, receivedSykmelding, loggingMeta)
                        }

                        val currentRequestLatency = requestLatency.observeDuration()

                        updateRedis(jedis, ediLoggId, sha256String)
                        log.info(
                            "Message got outcome {}, {}, processing took {}s",
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

    private fun getHelsepersonellKategori(godkjenninger: List<Godkjenning>): String? = when {
        godkjenninger.find { it.helsepersonellkategori?.verdi === "LE" } != null -> "LE"
        godkjenninger.find { it.helsepersonellkategori?.verdi === "TL" } != null -> "TL"
        godkjenninger.find { it.helsepersonellkategori?.verdi === "MT" } != null -> "MT"
        godkjenninger.find { it.helsepersonellkategori?.verdi === "FT" } != null -> "FT"
        godkjenninger.find { it.helsepersonellkategori?.verdi === "KI" } != null -> "KI"
        else -> {
            val verdi = godkjenninger.firstOrNull()?.helsepersonellkategori?.verdi
            log.warn("Signerende behandler har ikke en helsepersonellkategori($verdi) vi kjenner igjen")
            verdi
        }
    }
}
