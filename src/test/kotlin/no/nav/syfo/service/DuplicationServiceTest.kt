package no.nav.syfo.service

import io.mockk.clearAllMocks
import java.time.LocalDate
import java.time.LocalDateTime
import no.nav.helse.sm2013.Address
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.helse.sm2013.NavnType
import no.nav.helse.sm2013.TeleCom
import no.nav.syfo.duplicationcheck.model.Duplicate
import no.nav.syfo.duplicationcheck.model.DuplicateCheck
import no.nav.syfo.util.TestDB
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class DuplicationServiceTest {
    private val testDatabase = TestDB
    private val duplicationService = DuplicationService(testDatabase.database)

    @BeforeEach
    internal fun setup() {
        clearAllMocks()
    }

    @Test
    fun `Should not include strekkode`() {
        val sha256 = sha256hashstring(getHelseOpplysinginArbeidsuforhet("strekkode"))
        val sha256New = sha256hashstring(getHelseOpplysinginArbeidsuforhet("strekkode2"))
        assertEquals(sha256, sha256New)
    }

    @Test
    fun `Should not be same with differnt arbeidsgiver`() {
        val sha256 =
            sha256hashstring(getHelseOpplysinginArbeidsuforhet("strekkode", "arbeidsgiver"))
        val sha256New = sha256hashstring(getHelseOpplysinginArbeidsuforhet("strekkode"))
        assertNotEquals(sha256, sha256New)
    }

    @Test
    fun `Should return duplicationCheck if sha256HealthInformation is in database`() {
        val sykmeldingID = "as344f11-23132-2dddef1s232df"

        val sha256HealthInformation = "asdsad"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplicationCheck =
            DuplicateCheck(
                sykmeldingID,
                sha256HealthInformation,
                mottakId,
                "12-33",
                LocalDateTime.now(),
                epjSystem,
                epjVersion,
                orgNumber,
                null,
            )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck(sha256HealthInformation, mottakId)

        assertEquals(duplicationCheck.sha256HealthInformation, isDuplicat?.sha256HealthInformation)
        assertEquals(duplicationCheck.mottakId, isDuplicat?.mottakId)
        assertEquals(duplicationCheck.msgId, isDuplicat?.msgId)
        assertEquals(
            duplicationCheck.mottattDate.toLocalDate(),
            isDuplicat?.mottattDate?.toLocalDate()
        )
        assertEquals(duplicationCheck.epjSystem, isDuplicat?.epjSystem)
        assertEquals(duplicationCheck.epjVersion, isDuplicat?.epjVersion)
        assertEquals(duplicationCheck.orgNumber, isDuplicat?.orgNumber)
    }

    @Test
    fun `Should return null if sha256HealthInformation is not database`() {
        val sykmeldingID = "asdsdfsff11-23132-2dddefsdf"
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplicationCheck =
            DuplicateCheck(
                sykmeldingID,
                sha256HealthInformation,
                mottakId,
                "12-33",
                LocalDateTime.now(),
                epjSystem,
                epjVersion,
                orgNumber,
                null,
            )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck("1231", "1334")

        assertEquals(null, isDuplicat)
    }

    @Test
    fun `Should return duplicationCheck if mottakId is database`() {
        val sykmeldingID = "asdsadff11-23132-2ddde323"
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"

        val duplicationCheck =
            DuplicateCheck(
                sykmeldingID,
                sha256HealthInformation,
                mottakId,
                "12-33",
                LocalDateTime.now(),
                epjSystem,
                epjVersion,
                orgNumber,
                null,
            )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck("1231", mottakId)

        assertEquals(mottakId, isDuplicat?.mottakId)
    }

    @Test
    fun `Should return duplicationCheck if epjSystem,epjVersion and orgNumber is null`() {
        val sykmeldingID = "asds23ff11-23132-2dhsddsffsdf"
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"

        val duplicationCheck =
            DuplicateCheck(
                sykmeldingID,
                sha256HealthInformation,
                mottakId,
                "12-33",
                LocalDateTime.now(),
                "epj",
                "1",
                null,
                null,
            )

        duplicationService.persistDuplicationCheck(duplicationCheck)
        val isDuplicat = duplicationService.getDuplicationCheck("1231", mottakId)

        assertEquals(duplicationCheck.sha256HealthInformation, isDuplicat?.sha256HealthInformation)
        assertEquals(duplicationCheck.mottakId, isDuplicat?.mottakId)
        assertEquals(duplicationCheck.msgId, isDuplicat?.msgId)
        assertEquals(
            duplicationCheck.mottattDate.toLocalDate(),
            isDuplicat?.mottattDate?.toLocalDate()
        )
        assertEquals(duplicationCheck.epjSystem, isDuplicat?.epjSystem)
        assertEquals(duplicationCheck.epjVersion, isDuplicat?.epjVersion)
        assertEquals(duplicationCheck.orgNumber, isDuplicat?.orgNumber)
    }

    @Test
    fun `Should persiste duplication in database`() {
        val sykmeldingID = "asd13111-23132-2dddefssddf-12345f"
        val sha256HealthInformation = "asdsadff11"
        val mottakId = "1231-213"
        val msgId = "12-33"
        val epjSystem = "Kul EPJ"
        val epjVersion = "1.3.4"
        val orgNumber = "992312355"
        val mottatDato = LocalDateTime.now()

        val duplicationCheck =
            DuplicateCheck(
                sykmeldingID,
                sha256HealthInformation,
                mottakId,
                msgId,
                mottatDato,
                epjSystem,
                epjVersion,
                orgNumber,
                null,
            )

        duplicationService.persistDuplicationCheck(duplicationCheck)

        val isDuplicat = duplicationService.getDuplicationCheck("1231", mottakId)!!

        val duplication =
            Duplicate(
                sykmeldingID,
                mottakId,
                msgId,
                isDuplicat.sykmeldingId,
                mottatDato,
                epjSystem,
                epjVersion,
                orgNumber,
            )

        duplicationService.persistDuplication(duplication)

        assertEquals(mottakId, isDuplicat.mottakId)
    }
}

fun getHelseOpplysinginArbeidsuforhet(nyStrekkode: String, nyArbeidsgiver: String = "SAS AS") =
    HelseOpplysningerArbeidsuforhet().apply {
        arbeidsgiver =
            HelseOpplysningerArbeidsuforhet.Arbeidsgiver().apply {
                harArbeidsgiver =
                    CS().apply {
                        dn = "En arbeidsgiver"
                        v = "1"
                    }
                navnArbeidsgiver = nyArbeidsgiver
                yrkesbetegnelse = "Pilot"
                stillingsprosent = 100
            }
        kontaktMedPasient =
            HelseOpplysningerArbeidsuforhet.KontaktMedPasient().apply {
                kontaktDato = LocalDate.of(2023, 1, 1)
                behandletDato = LocalDate.of(2023, 1, 1).atStartOfDay()
            }
        behandler =
            HelseOpplysningerArbeidsuforhet.Behandler().apply {
                navn =
                    NavnType().apply {
                        fornavn = "Per"
                        etternavn = "Hansne"
                    }
                id.add(
                    Ident().apply {
                        id = "12343567"
                        typeId =
                            CV().apply {
                                dn = "Fødselsnummer"
                                s = "2.16.578.1.12.4.1.1.8116"
                                v = "FNR"
                            }
                    }
                )
                adresse = Address().apply {}
                kontaktInfo.add(TeleCom().apply {})
            }
        aktivitet =
            HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.of(2023, 1, 1)
                        periodeTOMDato = LocalDate.of(2023, 1, 1).plusDays(4)
                        aktivitetIkkeMulig =
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig()
                                .apply {
                                    medisinskeArsaker =
                                        ArsakType().apply {
                                            arsakskode.add(
                                                CS().apply {
                                                    v = "1"
                                                    dn =
                                                        "Helsetilstanden hindrer pasienten i å være i aktivitet"
                                                }
                                            )
                                            beskriv = "Kan ikkje jobbe"
                                        }
                                }
                    }
                )
            }
        pasient =
            HelseOpplysningerArbeidsuforhet.Pasient().apply {
                fodselsnummer =
                    Ident().apply {
                        id = "12343567"
                        typeId =
                            CV().apply {
                                dn = "Fødselsnummer"
                                s = "2.16.578.1.12.4.1.1.8116"
                                v = "FNR"
                            }
                    }
            }
        syketilfelleStartDato = LocalDate.of(2023, 1, 1)
        medisinskVurdering =
            HelseOpplysningerArbeidsuforhet.MedisinskVurdering().apply {
                hovedDiagnose =
                    HelseOpplysningerArbeidsuforhet.MedisinskVurdering.HovedDiagnose().apply {
                        diagnosekode =
                            CV().apply {
                                dn = "Problem med jus/politi"
                                s = "2.16.578.1.12.4.1.1.7110"
                                v = "Z09"
                            }
                    }
            }
        avsenderSystem =
            HelseOpplysningerArbeidsuforhet.AvsenderSystem().apply {
                systemNavn = "EPJ helse"
                systemVersjon = "1.0.2"
            }
        strekkode = nyStrekkode
    }
