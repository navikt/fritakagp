package no.nav.helse.fritakagp.web.api.resreq.validation

import no.nav.helse.AaregTestData
import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.web.api.resreq.GravidKravRequest
import no.nav.helse.fritakagp.web.api.resreq.validationShouldFailFor
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juli
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.september
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.valiktor.functions.validateForEach
import org.valiktor.validate

class AaregConstraintsKtTest {

    @Test
    fun `Ansatt slutter fram i tid`() {
        val periode = Arbeidsgiverperiode(
            15.januar(2021),
            20.januar(2021),
            4,
            månedsinntekt = 2590.8
        )

        validate(periode) {
            validate(Arbeidsgiverperiode::fom).maaHaAktivAnsettelsesperiode(periode, AaregTestData.ansettelsesperioderMedSluttDato)
        }
    }

    @Test
    fun `Refusjonskravet er innenfor Arbeidsforholdet`() {
        val periode = Arbeidsgiverperiode(
            15.januar(2021),
            18.januar(2021),
            2,
            månedsinntekt = 2590.8
        )

        validate(periode) {
            validate(Arbeidsgiverperiode::fom).maaHaAktivAnsettelsesperiode(periode, AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Sammenehengende arbeidsforhold slås sammen til en periode`() {
        val ansettelsesperioder = setOf(
            Periode(
                1.januar(2019),
                28.februar(2021)
            ),
            Periode(
                1.mars(2021),
                null
            )
        )

        val gravidKravRequest = GravidTestData.gravidKravRequestInValid.copy(
            perioder = listOf(
                Arbeidsgiverperiode(
                    15.januar(2021),
                    18.januar(2021),
                    2,
                    månedsinntekt = 2590.8
                ),
                Arbeidsgiverperiode(
                    26.februar(2021),
                    10.mars(2021),
                    12,
                    månedsinntekt = 2590.8
                )
            )
        )
        validate(gravidKravRequest) {
            validate(GravidKravRequest::perioder).validateForEach {
                validate(Arbeidsgiverperiode::fom).maaHaAktivAnsettelsesperiode(it, ansettelsesperioder)
            }
        }
    }

    @Test
    fun `Refusjonsdato er før Arbeidsforhold har begynt`() {
        val periode = Arbeidsgiverperiode(
            1.januar(2021),
            5.januar(2021),
            2,
            månedsinntekt = 2590.8
        )
        validationShouldFailFor(Arbeidsgiverperiode::fom) {
            validate(periode) {
                validate(Arbeidsgiverperiode::fom).maaHaAktivAnsettelsesperiode(
                    periode,
                    AaregTestData.paagaaendeAnsettelsesperiode
                )
            }
        }
    }

    @Test
    fun `Refusjonsdato begynner samtidig som Arbeidsforhold skal ikke feile`() {
        val periode = Arbeidsgiverperiode(
            5.februar(2021),
            9.februar(2021),
            2,
            månedsinntekt = 2590.8
        )
        validate(periode) {
            validate(Arbeidsgiverperiode::fom).maaHaAktivAnsettelsesperiode(
                periode,
                AaregTestData.paagaaendeAnsettelsesperiode
            )
        }
    }

    @Test
    fun `Refusjonsdato etter Arbeidsforhold er avsluttet`() {
        val periode = Arbeidsgiverperiode(
            15.mai(2021),
            18.mai(2021),
            2,
            månedsinntekt = 2590.8
        )

        validationShouldFailFor(Arbeidsgiverperiode::fom) {
            validate(periode) {
                validate(Arbeidsgiverperiode::fom).maaHaAktivAnsettelsesperiode(
                    periode,
                    AaregTestData.avsluttetAnsettelsesperiode
                )
            }
        }
    }

    @Test
    fun `merge fragmented periods`() {
        assertThat(
            slaaSammenPerioder(
                setOf(
                    // skal ble merget til 1 periode fra 1.1.21 til 28.2.21
                    Periode(
                        1.januar(2021),
                        29.januar(2021)
                    ),
                    Periode(
                        1.februar(2021),
                        13.februar(2021)
                    ),
                    Periode(
                        15.februar(2021),
                        28.februar(2021)
                    ),

                    // skal bli merget til 1
                    Periode(
                        20.mars(2021),
                        31.mars(2021)
                    ),
                    Periode(
                        2.april(2021),
                        30.april(2021)
                    ),

                    // skal bli merget til 1
                    Periode(
                        1.juli(2021),
                        30.august(2021)
                    ),
                    Periode(
                        1.september(2021),
                        null
                    )
                )
            )
        ).hasSize(3)

        assertThat(
            slaaSammenPerioder(
                setOf(
                    Periode(
                        1.januar(2021),
                        29.januar(2021)
                    ),
                    Periode(
                        1.september(2021),
                        null
                    )
                )
            )
        ).hasSize(2)

        assertThat(
            slaaSammenPerioder(
                setOf(
                    Periode(1.september(2021), null)
                )
            )
        ).hasSize(1)
    }
}
