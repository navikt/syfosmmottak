package no.nav.syfo.util

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk
import no.nav.helse.eiFellesformat.XMLEIFellesformat
import no.nav.helse.msgHead.XMLMsgHead
import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.helse.sm2013.TeleCom
import no.nav.syfo.Environment
import no.nav.syfo.apprec.Apprec
import no.nav.syfo.pdl.client.model.PdlIdent
import no.nav.syfo.pdl.model.PdlPerson
import org.amshove.kluent.shouldBeEqualTo
import org.apache.kafka.clients.producer.KafkaProducer
import redis.clients.jedis.Jedis
import java.io.StringReader
import java.time.LocalDate
import java.time.LocalDateTime

class CheckSM2013ContentTest : FunSpec({
    context("Check SM2013 content constinue loop or not") {
        test("Check if patient not found in PDL should return true") {
            val stringInput =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat

            val pasient = null
            val behandler = mockk<PdlPerson>(relaxed = true)
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val originaltPasientFnr = "10987654321"
            val loggingMeta = mockk<LoggingMeta>(relaxed = true)
            val ediLoggId = "12312"
            val msgId = "1231-232"
            val msgHead = fellesformat.get<XMLMsgHead>()
            val env = mockk<Environment>(relaxed = true)
            val kafkaproducerApprec = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
            val jedis = mockk<Jedis>(relaxed = true)
            val sha256String = ""

            val checkSM2013Content = checkSM2013Content(
                pasient,
                behandler,
                healthInformation,
                originaltPasientFnr,
                loggingMeta,
                fellesformat,
                ediLoggId,
                msgId,
                msgHead,
                env,
                kafkaproducerApprec,
                jedis,
                sha256String
            )

            checkSM2013Content shouldBeEqualTo true
        }

        test("Check if behandler not found in PDL should return true") {
            val stringInput =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat

            val pasient = PdlPerson(
                listOf(
                    PdlIdent("10987654321", false, "FOLKEREGISTERIDENT"),
                    PdlIdent("aktorId", false, "AKTORID")
                )
            )
            val behandler = null
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val originaltPasientFnr = "10987654321"
            val loggingMeta = mockk<LoggingMeta>(relaxed = true)
            val ediLoggId = "12312"
            val msgId = "1231-232"
            val msgHead = fellesformat.get<XMLMsgHead>()
            val env = mockk<Environment>(relaxed = true)
            val kafkaproducerApprec = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
            val jedis = mockk<Jedis>(relaxed = true)
            val sha256String = ""

            val checkSM2013Content = checkSM2013Content(
                pasient,
                behandler,
                healthInformation,
                originaltPasientFnr,
                loggingMeta,
                fellesformat,
                ediLoggId,
                msgId,
                msgHead,
                env,
                kafkaproducerApprec,
                jedis,
                sha256String
            )

            checkSM2013Content shouldBeEqualTo true
        }

        test("Check if aktivitet not found should return true") {
            val stringInput =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")
            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat

            val pasient = PdlPerson(
                listOf(
                    PdlIdent("10987654321", false, "FOLKEREGISTERIDENT"),
                    PdlIdent("aktorId", false, "AKTORID")
                )
            )
            val behandler = null
            val healthInformation = extractHelseOpplysningerArbeidsuforhet(fellesformat)
            val originaltPasientFnr = "10987654321"
            val loggingMeta = mockk<LoggingMeta>(relaxed = true)
            val ediLoggId = "12312"
            val msgId = "1231-232"
            val msgHead = fellesformat.get<XMLMsgHead>()
            val env = mockk<Environment>(relaxed = true)
            val kafkaproducerApprec = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
            val jedis = mockk<Jedis>(relaxed = true)
            val sha256String = ""

            val checkSM2013Content = checkSM2013Content(
                pasient,
                behandler,
                healthInformation,
                originaltPasientFnr,
                loggingMeta,
                fellesformat,
                ediLoggId,
                msgId,
                msgHead,
                env,
                kafkaproducerApprec,
                jedis,
                sha256String
            )

            checkSM2013Content shouldBeEqualTo true
        }

        test("Check if aktivitet found should return false") {
            val stringInput =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")

            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat

            val pasientPDL = PdlPerson(
                listOf(
                    PdlIdent("10987654321", false, "FOLKEREGISTERIDENT"),
                    PdlIdent("aktorId", false, "AKTORID")
                )
            )

            val behandlerNull = PdlPerson(
                listOf(
                    PdlIdent("10987654321", false, "FOLKEREGISTERIDENT"),
                    PdlIdent("aktorId", false, "AKTORID")
                )
            )
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                    harArbeidsgiver = CS().apply {
                        dn = "En arbeidsgiver"
                        v = "1"
                    }
                    navnArbeidsgiver = "SAS as"
                    yrkesbetegnelse = "Pilot"
                    stillingsprosent = 100
                }
                kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                    kontaktDato = LocalDate.now()
                    behandletDato = LocalDateTime.now()
                }
                behandler = HelseOpplysningerArbeidsuforhet.Behandler().apply {
                    navn = NavnType().apply {
                        fornavn = "Per"
                        etternavn = "Hansne"
                    }
                    id.add(
                        Ident().apply {
                            id = "12343567"
                            typeId = CV().apply {
                                dn = "Fødselsnummer"
                                s = "2.16.578.1.12.4.1.1.8116"
                                v = "FNR"
                            }
                        }
                    )
                    adresse = Address().apply {
                    }
                    kontaktInfo.add(
                        TeleCom().apply {
                        }
                    )
                }
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                        HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                            periodeFOMDato = LocalDate.now()
                            periodeTOMDato = LocalDate.now().plusDays(4)
                            aktivitetIkkeMulig =
                                HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                                    medisinskeArsaker = ArsakType().apply {
                                        arsakskode.add(
                                            CS().apply {
                                                v = "1"
                                                dn = "Helsetilstanden hindrer pasienten i å være i aktivitet"
                                            }
                                        )
                                        beskriv = "Kan ikkje jobbe"
                                    }
                                }
                        }
                    )
                }
                pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
                    fodselsnummer = Ident().apply {
                        id = "12343567"
                        typeId = CV().apply {
                            dn = "Fødselsnummer"
                            s = "2.16.578.1.12.4.1.1.8116"
                            v = "FNR"
                        }
                    }
                }
                syketilfelleStartDato = LocalDate.now()
                medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                    hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                        diagnosekode = CV().apply {
                            dn = "Problem med jus/politi"
                            s = "2.16.578.1.12.4.1.1.7110"
                            v = "Z09"
                        }
                    }
                }
                avsenderSystem = HelseOpplysningerArbeidsuforhet.AvsenderSystem().apply {
                    systemNavn = "EPJ helse"
                    systemVersjon = "1.0.2"
                }
            }
            val originaltPasientFnr = "10987654321"
            val loggingMeta = mockk<LoggingMeta>(relaxed = true)
            val ediLoggId = "12312"
            val msgId = "1231-232"
            val msgHead = fellesformat.get<XMLMsgHead>()
            val env = mockk<Environment>(relaxed = true)
            val kafkaproducerApprec = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
            val jedis = mockk<Jedis>(relaxed = true)
            val sha256String = ""

            val checkSM2013Content = checkSM2013Content(
                pasientPDL,
                behandlerNull,
                healthInformation,
                originaltPasientFnr,
                loggingMeta,
                fellesformat,
                ediLoggId,
                msgId,
                msgHead,
                env,
                kafkaproducerApprec,
                jedis,
                sha256String
            )

            checkSM2013Content shouldBeEqualTo false
        }

        test("Check if aktivitet not found should return true") {
            val stringInput =
                no.nav.syfo.utils.getFileAsString("src/test/resources/fellesformat.xml")

            val fellesformat = fellesformatUnmarshaller.unmarshal(StringReader(stringInput)) as XMLEIFellesformat

            val pasientPDL = PdlPerson(
                listOf(
                    PdlIdent("10987654321", false, "FOLKEREGISTERIDENT"),
                    PdlIdent("aktorId", false, "AKTORID")
                )
            )

            val behandlerNull = null
            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                arbeidsgiver = HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                    harArbeidsgiver = CS().apply {
                        dn = "En arbeidsgiver"
                        v = "1"
                    }
                    navnArbeidsgiver = "SAS as"
                    yrkesbetegnelse = "Pilot"
                    stillingsprosent = 100
                }
                kontaktMedPasient = HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                    kontaktDato = LocalDate.now()
                    behandletDato = LocalDateTime.now()
                }
                behandler = HelseOpplysningerArbeidsuforhet.Behandler().apply {
                    navn = NavnType().apply {
                        fornavn = "Per"
                        etternavn = "Hansne"
                    }
                    id.add(
                        Ident().apply {
                            id = "12343567"
                            typeId = CV().apply {
                                dn = "Fødselsnummer"
                                s = "2.16.578.1.12.4.1.1.8116"
                                v = "FNR"
                            }
                        }
                    )
                    adresse = Address().apply {
                    }
                    kontaktInfo.add(
                        TeleCom().apply {
                        }
                    )
                }
                aktivitet = null
                pasient = HelseOpplysningerArbeidsuforhet.Pasient().apply {
                    fodselsnummer = Ident().apply {
                        id = "12343567"
                        typeId = CV().apply {
                            dn = "Fødselsnummer"
                            s = "2.16.578.1.12.4.1.1.8116"
                            v = "FNR"
                        }
                    }
                }
                syketilfelleStartDato = LocalDate.now()
                medisinskVurdering = HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                    hovedDiagnose = HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                        diagnosekode = CV().apply {
                            dn = "Problem med jus/politi"
                            s = "2.16.578.1.12.4.1.1.7110"
                            v = "Z09"
                        }
                    }
                }
                avsenderSystem = HelseOpplysningerArbeidsuforhet.AvsenderSystem().apply {
                    systemNavn = "EPJ helse"
                    systemVersjon = "1.0.2"
                }
            }
            val originaltPasientFnr = "10987654321"
            val loggingMeta = mockk<LoggingMeta>(relaxed = true)
            val ediLoggId = "12312"
            val msgId = "1231-232"
            val msgHead = fellesformat.get<XMLMsgHead>()
            val env = mockk<Environment>(relaxed = true)
            val kafkaproducerApprec = mockk<KafkaProducer<String, Apprec>>(relaxed = true)
            val jedis = mockk<Jedis>(relaxed = true)
            val sha256String = ""

            val checkSM2013Content = checkSM2013Content(
                pasientPDL,
                behandlerNull,
                healthInformation,
                originaltPasientFnr,
                loggingMeta,
                fellesformat,
                ediLoggId,
                msgId,
                msgHead,
                env,
                kafkaproducerApprec,
                jedis,
                sha256String
            )

            checkSM2013Content shouldBeEqualTo true
        }
    }
})
