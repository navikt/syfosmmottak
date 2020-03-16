package no.nav.syfo.application

import io.ktor.util.KtorExperimentalAPI
import java.io.StringReader
import java.time.ZoneOffset
import java.util.UUID
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage
import kotlinx.coroutines.delay
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.fields
import no.nav.emottak.subscription.SubscriptionPort
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.eiFellesformat.XMLMottakenhetBlokk
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.syfo.Environment
import no.nav.syfo.VaultCredentials
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.client.AktoerIdClient
import no.nav.syfo.client.ArbeidsFordelingClient
import no.nav.syfo.client.SarClient
import no.nav.syfo.client.SyfoSykemeldingRuleClient
import no.nav.syfo.client.findBestSamhandlerPraksis
import no.nav.syfo.handlestatus.handleAktivitetOrPeriodeIsMissing
import no.nav.syfo.handlestatus.handleAnnenFraversArsakkodeVIsmissing
import no.nav.syfo.handlestatus.handleArbeidsplassenArsakskodeIsmissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeBeskrivelseMissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeIsMissing
import no.nav.syfo.handlestatus.handleBiDiagnoserDiagnosekodeVerkIsMissing
import no.nav.syfo.handlestatus.handleDoctorNotFoundInAktorRegister
import no.nav.syfo.handlestatus.handleDuplicateEdiloggid
import no.nav.syfo.handlestatus.handleDuplicateSM2013Content
import no.nav.syfo.handlestatus.handleFnrAndDnrIsmissingFromBehandler
import no.nav.syfo.handlestatus.handleHouvedDiagnoseDiagnoseBeskrivelseMissing
import no.nav.syfo.handlestatus.handleHouvedDiagnoseDiagnosekodeMissing
import no.nav.syfo.handlestatus.handleMedisinskeArsakskodeIsmissing
import no.nav.syfo.handlestatus.handlePatientNotFoundInAktorRegister
import no.nav.syfo.handlestatus.handleStatusINVALID
import no.nav.syfo.handlestatus.handleStatusMANUALPROCESSING
import no.nav.syfo.handlestatus.handleStatusOK
import no.nav.syfo.handlestatus.handleTestFnrInProd
import no.nav.syfo.log
import no.nav.syfo.metrics.INCOMING_MESSAGE_COUNTER
import no.nav.syfo.metrics.REQUEST_TIME
import no.nav.syfo.metrics.ULIK_SENDER_OG_BEHANDLER
import no.nav.syfo.model.ManuellOppgave
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Status
import no.nav.syfo.model.ValidationResult
import no.nav.syfo.model.toSykmelding
import no.nav.syfo.sak.avro.ProduceTask
import no.nav.syfo.service.samhandlerParksisisLegevakt
import no.nav.syfo.service.sha256hashstring
import no.nav.syfo.service.startSubscription
import no.nav.syfo.service.updateRedis
import no.nav.syfo.util.LoggingMeta
import no.nav.syfo.util.annenFraversArsakkodeVMangler
import no.nav.syfo.util.arbeidsplassenArsakskodeMangler
import no.nav.syfo.util.countNewDiagnoseCode
import no.nav.syfo.util.erTestFnr
import no.nav.syfo.util.extractHelseOpplysningerArbeidsuforhet
import no.nav.syfo.util.extractOrganisationHerNumberFromSender
import no.nav.syfo.util.extractOrganisationNumberFromSender
import no.nav.syfo.util.extractOrganisationRashNumberFromSender
import no.nav.syfo.util.fellesformatUnmarshaller
import no.nav.syfo.util.fnrOgDnrMangler
import no.nav.syfo.util.get
import no.nav.syfo.util.medisinskeArsakskodeMangler
import no.nav.syfo.util.wrapExceptions
import no.nav.tjeneste.pip.egen.ansatt.v1.EgenAnsattV1
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import org.apache.kafka.clients.producer.KafkaProducer
import redis.clients.jedis.Jedis
import redis.clients.jedis.exceptions.JedisConnectionException

class BlockingApplicationRunner {

    @KtorExperimentalAPI
    suspend fun run(
        inputconsumer: MessageConsumer,
        syfoserviceProducer: MessageProducer,
        backoutProducer: MessageProducer,
        subscriptionEmottak: SubscriptionPort,
        kafkaproducerreceivedSykmelding: KafkaProducer<String, ReceivedSykmelding>,
        kafkaproducervalidationResult: KafkaProducer<String, ValidationResult>,
        syfoSykemeldingRuleClient: SyfoSykemeldingRuleClient,
        kuhrSarClient: SarClient,
        aktoerIdClient: AktoerIdClient,
        arbeidsFordelingClient: ArbeidsFordelingClient,
        env: Environment,
        credentials: VaultCredentials,
        applicationState: ApplicationState,
        jedis: Jedis,
        kafkaManuelTaskProducer: KafkaProducer<String, ProduceTask>,
        session: Session,
        kafkaproducerApprec: KafkaProducer<String, Apprec>,
        kafkaproducerManuellOppgave: KafkaProducer<String, ManuellOppgave>,
        personV3: PersonV3,
        egenAnsattV1: EgenAnsattV1
    ) {
        wrapExceptions {

            loop@ while (applicationState.ready) {
                val message = inputconsumer.receiveNoWait()
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
                    val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(inputMessageText)) as XMLEIFellesformat
                    val receiverBlock = fellesformat.get<XMLMottakenhetBlokk>()
                    val msgHead = fellesformat.get<XMLMsgHead>()

                    val loggingMeta = LoggingMeta(
                            mottakId = receiverBlock.ediLoggId,
                            orgNr = extractOrganisationNumberFromSender(fellesformat)?.id,
                            msgId = msgHead.msgInfo.msgId
                    )

                    val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
                    val ediLoggId = receiverBlock.ediLoggId
                    val sha256String = sha256hashstring(healthInformation)
                    val msgId = msgHead.msgInfo.msgId

                    val legekontorHerId = extractOrganisationHerNumberFromSender(fellesformat)?.id
                    val legekontorReshId = extractOrganisationRashNumberFromSender(fellesformat)?.id
                    val legekontorOrgNr = extractOrganisationNumberFromSender(fellesformat)?.id
                    val legekontorOrgName = msgHead.msgInfo.sender.organisation.organisationName

                    val personNumberPatient = healthInformation.pasient.fodselsnummer.id
                    val personNumberDoctor = receiverBlock.avsenderFnrFraDigSignatur

                    log.info("Received message, {}", StructuredArguments.fields(loggingMeta))

                    val aktoerIds = aktoerIdClient.getAktoerIds(
                            listOf(personNumberDoctor, personNumberPatient),
                            credentials.serviceuserUsername,
                            loggingMeta)

                    log.info("Ferdig med aktoerIdClient {}", StructuredArguments.fields(loggingMeta))

                    val samhandlerInfo = kuhrSarClient.getSamhandler(personNumberDoctor)
                    val samhandlerPraksisMatch = findBestSamhandlerPraksis(
                            samhandlerInfo,
                            legekontorOrgName,
                            legekontorHerId,
                            loggingMeta)
                    val samhandlerPraksis = samhandlerPraksisMatch?.samhandlerPraksis

                    if (samhandlerPraksisMatch == null &&
                            samhandlerInfo.firstOrNull()?.samh_praksis != null &&
                            samhandlerInfo.firstOrNull()?.samh_praksis?.firstOrNull() != null) {
                            val firstSamhnalderPraksis = samhandlerInfo.firstOrNull()?.samh_praksis?.firstOrNull()
                            if (firstSamhnalderPraksis != null) {
                                log.info("Siste utvei med tss matching ble samhandler praksis: " +
                                        "Orgnumer: ${firstSamhnalderPraksis.org_id} " +
                                        "Navn: ${firstSamhnalderPraksis.navn} " +
                                        "Tssid: ${firstSamhnalderPraksis.tss_ident} " +
                                        "Adresselinje1: ${firstSamhnalderPraksis.arbeids_adresse_linje_1} " +
                                        "Samhandler praksis type: ${firstSamhnalderPraksis.samh_praksis_type_kode} " +
                                        "{}", fields(loggingMeta))
                            }
                    }

                    if (samhandlerPraksisMatch?.percentageMatch != null && samhandlerPraksisMatch.percentageMatch == 999.0) {
                        log.info("SamhandlerPraksis is found but is FALE or FALO, subscription_emottak is not created, {}", StructuredArguments.fields(loggingMeta))
                    } else {
                        when (samhandlerPraksis) {
                            null -> log.info("SamhandlerPraksis is Not found, {}", StructuredArguments.fields(loggingMeta))
                            else -> if (!samhandlerParksisisLegevakt(samhandlerPraksis) &&
                                    !receiverBlock.partnerReferanse.isNullOrEmpty() &&
                                    receiverBlock.partnerReferanse.isNotBlank()) {
                                startSubscription(subscriptionEmottak, samhandlerPraksis, msgHead, receiverBlock, loggingMeta)
                            } else {
                                log.info("SamhandlerPraksis is Legevakt or partnerReferanse is empty or blank, subscription_emottak is not created, {}", StructuredArguments.fields(loggingMeta))
                            }
                        }
                    }

                    val redisSha256String = jedis.get(sha256String)
                    val redisEdiloggid = jedis.get(ediLoggId)

                    if (redisSha256String != null) {
                        handleDuplicateSM2013Content(redisSha256String, loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec)
                        continue@loop
                    } else if (redisEdiloggid != null && redisEdiloggid.length != 21) {
                        log.error("Redis returned a redisEdiloggid that is longer than 21" +
                                "characters redisEdiloggid: {} {}", redisEdiloggid, StructuredArguments.fields(loggingMeta))
                        throw RuntimeException("Redis has some issues with geting the redisEdiloggid")
                    } else if (redisEdiloggid != null) {
                        handleDuplicateEdiloggid(redisEdiloggid, loggingMeta, fellesformat,
                                ediLoggId, msgId, msgHead, env, kafkaproducerApprec)
                        continue@loop
                    } else {
                        val patientIdents = aktoerIds[personNumberPatient]
                        val doctorIdents = aktoerIds[personNumberDoctor]

                        if (patientIdents == null || patientIdents.feilmelding != null) {
                            handlePatientNotFoundInAktorRegister(patientIdents, loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }
                        if (doctorIdents == null || doctorIdents.feilmelding != null) {
                            handleDoctorNotFoundInAktorRegister(doctorIdents, loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (healthInformation.aktivitet == null || healthInformation.aktivitet.periode.isNullOrEmpty()) {
                            handleAktivitetOrPeriodeIsMissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.biDiagnoser != null &&
                                healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.v.isNullOrEmpty() }) {
                            handleBiDiagnoserDiagnosekodeIsMissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.biDiagnoser != null &&
                                healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.s.isNullOrEmpty() }) {
                            handleBiDiagnoserDiagnosekodeVerkIsMissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.biDiagnoser != null &&
                                healthInformation.medisinskVurdering.biDiagnoser.diagnosekode.any { it.dn.isNullOrEmpty() }) {
                            handleBiDiagnoserDiagnosekodeBeskrivelseMissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (fnrOgDnrMangler(healthInformation)) {
                            handleFnrAndDnrIsmissingFromBehandler(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.hovedDiagnose?.diagnosekode != null &&
                                healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.v == null) {
                            handleHouvedDiagnoseDiagnosekodeMissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (healthInformation.medisinskVurdering?.hovedDiagnose?.diagnosekode != null &&
                                healthInformation.medisinskVurdering.hovedDiagnose.diagnosekode.dn == null) {
                            handleHouvedDiagnoseDiagnoseBeskrivelseMissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (medisinskeArsakskodeMangler(healthInformation)) {
                            handleMedisinskeArsakskodeIsmissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (arbeidsplassenArsakskodeMangler(healthInformation)) {
                            handleArbeidsplassenArsakskodeIsmissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (erTestFnr(personNumberPatient) && env.cluster == "prod-fss") {
                            handleTestFnrInProd(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        if (annenFraversArsakkodeVMangler(healthInformation)) {
                            handleAnnenFraversArsakkodeVIsmissing(loggingMeta, fellesformat,
                                    ediLoggId, msgId, msgHead, env, kafkaproducerApprec, jedis, sha256String)
                            continue@loop
                        }

                        val sykmelding = healthInformation.toSykmelding(
                                sykmeldingId = UUID.randomUUID().toString(),
                                pasientAktoerId = patientIdents.identer!!.first().ident,
                                legeAktoerId = doctorIdents.identer!!.first().ident,
                                msgId = msgId,
                                signaturDato = msgHead.msgInfo.genDate
                        )
                        val receivedSykmelding = ReceivedSykmelding(
                                sykmelding = sykmelding,
                                personNrPasient = personNumberPatient,
                                tlfPasient = healthInformation.pasient.kontaktInfo.firstOrNull()?.teleAddress?.v,
                                personNrLege = personNumberDoctor,
                                navLogId = ediLoggId,
                                msgId = msgId,
                                legekontorOrgNr = legekontorOrgNr,
                                legekontorOrgName = legekontorOrgName,
                                legekontorHerId = legekontorHerId,
                                legekontorReshId = legekontorReshId,
                                mottattDato = receiverBlock.mottattDatotid.toGregorianCalendar().toZonedDateTime().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(),
                                rulesetVersion = healthInformation.regelSettVersjon,
                                fellesformat = inputMessageText,
                                tssid = samhandlerPraksis?.tss_ident ?: ""
                        )

                        if (receivedSykmelding.sykmelding.behandler.fnr != personNumberDoctor) {
                            ULIK_SENDER_OG_BEHANDLER.inc()
                            log.info("Behandlers fnr og avsendres fnr stemmer ikkje {}", StructuredArguments.fields(loggingMeta))
                        }

                        countNewDiagnoseCode(receivedSykmelding.sykmelding.medisinskVurdering)

                        log.info("Validating against rules, sykmeldingId {},  {}", StructuredArguments.keyValue("sykmeldingId", sykmelding.id), StructuredArguments.fields(loggingMeta))
                        val validationResult = syfoSykemeldingRuleClient.executeRuleValidation(receivedSykmelding)

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
                                    env.syfoSmManuellTopic,
                                    personV3,
                                    egenAnsattV1,
                                    arbeidsFordelingClient
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
                                    msgHead)
                        }

                        val currentRequestLatency = requestLatency.observeDuration()

                        updateRedis(jedis, ediLoggId, sha256String)
                        log.info("Message got outcome {}, {}, processing took {}s",
                                StructuredArguments.keyValue("status", validationResult.status),
                                StructuredArguments.keyValue("ruleHits", validationResult.ruleHits.joinToString(", ", "(", ")") { it.ruleName }),
                                StructuredArguments.keyValue("latency", currentRequestLatency),
                                StructuredArguments.fields(loggingMeta))
                    }
                } catch (jedisException: JedisConnectionException) {
                    log.error("Exception caught, redis issue while handling message, sending to backout", jedisException)
                    backoutProducer.send(message)
                    log.error("Setting applicationState.alive to false")
                    applicationState.alive = false
                } catch (e: Exception) {
                    log.error("Exception caught while handling message, sending to backout", e)
                    backoutProducer.send(message)
                }
            }
        }
    }
}
