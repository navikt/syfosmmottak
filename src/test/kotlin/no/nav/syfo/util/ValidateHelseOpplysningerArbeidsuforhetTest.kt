package no.nav.syfo.util

import io.kotest.core.spec.style.FunSpec
import no.nav.helse.sm2013.ArsakType
import no.nav.helse.sm2013.CS
import no.nav.helse.sm2013.HelseOpplysningerArbeidsuforhet
import org.amshove.kluent.shouldBeEqualTo
import java.time.LocalDate

class ValidateHelseOpplysningerArbeidsuforhetTest : FunSpec({
    context("Validering av mottatt sykmelding") {
        test("periodetypeIkkeAngitt er true hvis periodetype mangler (en periode)") {
            val aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
                periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now()
                        periodeTOMDato = LocalDate.now().plusDays(4)
                    }
                )
            }

            periodetypeIkkeAngitt(aktivitet) shouldBeEqualTo true
        }
        test("periodetypeIkkeAngitt er true hvis periodetype mangler for en av to perioder") {
            val aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
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
                                    beskriv = "Er syk"
                                }
                            }
                    }
                )
                periode.add(
                    HelseOpplysningerArbeidsuforhet.Aktivitet.Periode().apply {
                        periodeFOMDato = LocalDate.now().plusDays(5)
                        periodeTOMDato = LocalDate.now().plusDays(10)
                    }
                )
            }

            periodetypeIkkeAngitt(aktivitet) shouldBeEqualTo true
        }
        test("periodetypeIkkeAngitt er false hvis periodetype er angitt") {
            val aktivitet = HelseOpplysningerArbeidsuforhet.Aktivitet().apply {
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
                                    beskriv = "Er syk"
                                }
                            }
                    }
                )
            }

            periodetypeIkkeAngitt(aktivitet) shouldBeEqualTo false
        }
    }
})
