package no.nav.syfo

import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.CV
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import no.nav.helse.sm2013.Ident
import no.nav.syfo.model.toSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate

object SykmeldingsMapperSpek : Spek({

    describe("Check sykmeldings mapping") {

        it("Validate MedisinskeArsaker Arsakskode is mapped") {

            val healthInformation = HelseOpplysningerArbeidsuforhet().apply {
                aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                    periode.add(
                            HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                                periodeFOMDato = LocalDate.now()
                                periodeTOMDato = LocalDate.now().plusDays(4)
                                aktivitetIkkeMulig = HelseOpplysningerArbeidsuforhet.Aktivitet.Periode.AktivitetIkkeMulig().apply {
                                    medisinskeArsaker = ArsakType().apply {
                                        arsakskode.add(CS().apply {
                                            v = "1"
                                            dn = "Helsetilstanden hindrer pasienten i å være i aktivitet"
                                        })
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
            }

            val sykmelding = healthInformation.toSykmelding(
                    sykmeldingId = "123-asdasasd-12314234",
                    pasientAktoerId = "756564123",
                    legeAktoerId = "756564124",
                    msgId = "12313-12313-123123as-asda"
            )

            sykmelding.perioder.first().aktivitetIkkeMulig?.medisinskArsak?.arsak?.first()?.codeValue shouldEqual "1"
        }
    }
})
