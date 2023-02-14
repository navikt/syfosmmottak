package no.nav.syfo

import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.DynaSvarType
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.helse.sm2013.TeleCom
import no.nav.syfo.model.toDiagnose
import no.nav.syfo.model.toMeldingTilNAV
import no.nav.syfo.model.toPrognose
import no.nav.syfo.model.toSykmelding
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

internal class SykmeldingMapperTest {

    @Test
    internal fun `Validate MedisinskeArsaker Arsakskode is mapped`() {
        val medisinskeArsakerArsakskodeV = " 1"

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
                                            v = medisinskeArsakerArsakskodeV
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

        val sykmelding = healthInformation.toSykmelding(
            sykmeldingId = "123-asdasasd-12314234",
            pasientAktoerId = "756564123",
            legeAktoerId = "756564124",
            msgId = "12313-12313-123123as-asda",
            signaturDato = LocalDateTime.now(),
            behandlerFnr = "1213415151",
            behandlerHprNr = "00415151"
        )
        Assertions.assertEquals(
            medisinskeArsakerArsakskodeV.trim(),
            sykmelding.perioder.first().aktivitetIkkeMulig?.medisinskArsak?.arsak?.first()?.codeValue
        )
    }

    @Test
    internal fun `Validate Restriksjonskode is mapped`() {

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
            utdypendeOpplysninger = HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger().apply {
                spmGruppe.add(
                    HelseOpplysningerArbeidsuforhet.UtdypendeOpplysninger.SpmGruppe().apply {
                        spmGruppeId = "6.1"
                        spmGruppeTekst = "Utdypende opplysninger ved 4,12 og 28 uker ved visse diagnoser"
                        spmSvar.add(
                            DynaSvarType().apply {
                                spmId = "6.1.3"
                                spmTekst = "Hva er videre plan for behandling?"
                                restriksjon = DynaSvarType.Restriksjon().apply {
                                    restriksjonskode.add(
                                        CS().apply {
                                        }
                                    )
                                }
                                svarTekst = "-"
                            }
                        )
                    }
                )
            }
        }

        val sykmelding = healthInformation.toSykmelding(
            sykmeldingId = "123-asdasasd-12314234",
            pasientAktoerId = "756564123",
            legeAktoerId = "756564124",
            msgId = "12313-12313-123123as-asda",
            signaturDato = LocalDateTime.now(),
            behandlerFnr = "1213415151",
            behandlerHprNr = "00415151"
        )

        Assertions.assertEquals(
            emptyList<no.nav.syfo.model.SvarRestriksjon>(),
            sykmelding.utdypendeOpplysninger.getValue("6.1").getValue("6.1.3").restriksjoner
        )
    }

    @Test
    internal fun `Fjerner punktum fra diagnosekode`() {
        val originalDiagnosekode = CV().apply {
            dn = "Problem med jus/politi"
            s = "2.16.578.1.12.4.1.1.7110"
            v = "Z.09"
        }

        val mappetDiagnosekode = originalDiagnosekode.toDiagnose()

        Assertions.assertEquals("Z09", mappetDiagnosekode.kode)
    }

    @Test
    internal fun `Endrer ikke diagnosekode uten punktum`() {
        val originalDiagnosekode = CV().apply {
            dn = "Problem med jus/politi"
            s = "2.16.578.1.12.4.1.1.7110"
            v = "Z09"
        }

        val mappetDiagnosekode = originalDiagnosekode.toDiagnose()

        Assertions.assertEquals("Z09", mappetDiagnosekode.kode)
    }

    @Test
    internal fun `Diagnosekode skal ha stor forbokstav`() {
        val originalDiagnosekode = CV().apply {
            dn = "gallestein"
            s = "2.16.578.1.12.4.1.1.7110"
            v = "k801"
        }

        val mappetDiagnosekode = originalDiagnosekode.toDiagnose()

        Assertions.assertEquals("K801", mappetDiagnosekode.kode)
    }

    @Test
    internal fun `Setter bistandUmiddelbart til true hvis beskrivBistandNAV er angitt og regelsettversjon 3`() {
        val originalMeldingTilNAV = HelseOpplysningerArbeidsuforhet.MeldingTilNav().apply {
            isBistandNAVUmiddelbart = null
            beskrivBistandNAV = "Trenger bistand"
        }

        val mappetMeldingTilNAV = originalMeldingTilNAV.toMeldingTilNAV("3")

        Assertions.assertEquals(true, mappetMeldingTilNAV.bistandUmiddelbart)
        Assertions.assertEquals("Trenger bistand", mappetMeldingTilNAV.beskrivBistand)
    }

    @Test
    internal fun `Setter bistandUmiddelbart til false hvis isBistandNAVUmiddelbart er false og regelsettversjon 2`() {
        val originalMeldingTilNAV = HelseOpplysningerArbeidsuforhet.MeldingTilNav().apply {
            isBistandNAVUmiddelbart = false
            beskrivBistandNAV = "Trenger bistand"
        }

        val mappetMeldingTilNAV = originalMeldingTilNAV.toMeldingTilNAV("2")

        Assertions.assertEquals(false, mappetMeldingTilNAV.bistandUmiddelbart)
        Assertions.assertEquals("Trenger bistand", mappetMeldingTilNAV.beskrivBistand)
    }

    @Test
    internal fun `Setter bistandUmiddelbart til true hvis isBistandNAVUmiddelbart er true`() {
        val originalMeldingTilNAV = HelseOpplysningerArbeidsuforhet.MeldingTilNav().apply {
            isBistandNAVUmiddelbart = true
            beskrivBistandNAV = "Trenger bistand"
        }

        val mappetMeldingTilNAV = originalMeldingTilNAV.toMeldingTilNAV("2")

        Assertions.assertEquals(true, mappetMeldingTilNAV.bistandUmiddelbart)
        Assertions.assertEquals("Trenger bistand", mappetMeldingTilNAV.beskrivBistand)
    }

    @Test
    internal fun `Tom meldingTilNAV gir tom meldingTilNAV for regelsettversjon 3`() {
        val originalMeldingTilNAV = HelseOpplysningerArbeidsuforhet.MeldingTilNav()

        val mappetMeldingTilNAV = originalMeldingTilNAV.toMeldingTilNAV("3")

        Assertions.assertEquals(false, mappetMeldingTilNAV.bistandUmiddelbart)
        Assertions.assertEquals(null, mappetMeldingTilNAV.beskrivBistand)
    }

    @Test
    internal fun `Setter arbeidsforEtterPeriode til true`() {
        val originalPrognose =
            HelseOpplysningerArbeidsuforhet.Prognose().apply { isArbeidsforEtterEndtPeriode = true }

        val mappetMeldingTilNAV = originalPrognose.toPrognose()

        Assertions.assertEquals(true, mappetMeldingTilNAV.arbeidsforEtterPeriode)
    }

    @Test
    internal fun `Setter arbeidsforEtterPeriode til false`() {
        val originalPrognose =
            HelseOpplysningerArbeidsuforhet.Prognose().apply { isArbeidsforEtterEndtPeriode = false }

        val mappetMeldingTilNAV = originalPrognose.toPrognose()

        Assertions.assertEquals(false, mappetMeldingTilNAV.arbeidsforEtterPeriode)
    }
}
