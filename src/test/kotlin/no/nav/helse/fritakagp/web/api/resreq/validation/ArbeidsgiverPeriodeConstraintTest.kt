package no.nav.helse.fritakagp.web.api.resreq.validation

import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.domain.AgpFelter
import no.nav.helse.fritakagp.domain.ArbeidsgiverperiodeNy
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.Periode
import no.nav.helse.fritakagp.web.api.resreq.validationShouldFailFor
import org.junit.jupiter.api.Test
import org.valiktor.validate
import java.time.LocalDate

class ArbeidsgiverPeriodeConstraintTest {

    @Test
    fun `ikke overstigge 16 dager`() {
        val perioder = listOf<ArbeidsgiverperiodeNy>(
            ArbeidsgiverperiodeNy(
                perioder = listOf(
                    Periode(LocalDate.parse("2022-01-5"), LocalDate.parse("2022-01-8")),
                    Periode(LocalDate.parse("2022-01-12"), LocalDate.parse("2022-01-15")),
                    Periode(LocalDate.parse("2022-01-17"), LocalDate.parse("2022-01-18"))
                )
            ).also {
                it.felter = AgpFelter(antallDagerMedRefusjon = 16, månedsinntekt = 1000.0)
            },
            ArbeidsgiverperiodeNy(
                perioder = listOf(
                    Periode(LocalDate.parse("2022-02-15"), LocalDate.parse("2022-02-30"))
                )
            ).also {
                it.felter = AgpFelter(antallDagerMedRefusjon = 16, månedsinntekt = 2000.0)
            },
            ArbeidsgiverperiodeNy(
                perioder = listOf(
                    Periode(LocalDate.parse("2022-03-3"), LocalDate.parse("2022-03-8"))
                )
            ).also {
                it.felter = AgpFelter(antallDagerMedRefusjon = 16, månedsinntekt = 3000.0)
            }
        )
        val gravidKrav = GravidTestData.gravidKrav.copy(perioder = perioder)
        validationShouldFailFor(GravidKrav::perioder) {
            validate(gravidKrav) {
                validate(GravidKrav::perioder).oppholdOverstiger16dager()
            }
        }
    }
}