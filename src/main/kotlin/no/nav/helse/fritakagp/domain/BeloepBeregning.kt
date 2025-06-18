package no.nav.helse.fritakagp.domain

import no.nav.helse.fritakagp.integration.GrunnbeloepClient
import java.math.BigDecimal
import java.math.RoundingMode

class BeloepBeregning(
    private val grunnbeloepClient: GrunnbeloepClient
) {
    fun beregnBeloepKronisk(krav: KroniskKrav) = beregnPeriodeData(krav.perioder, krav.antallDager)

    fun beregnBeloepGravid(krav: GravidKrav) = beregnPeriodeData(krav.perioder, krav.antallDager)

    private fun beregnPeriodeData(perioder: List<Arbeidsgiverperiode>, antallDager: Int) {
        perioder.forEach { periode ->
            val grunnbeloep = grunnbeloepClient.hentGrunnbeloep(periode.fom)
            val seksG = BigDecimal(grunnbeloep).multiply(BigDecimal(6))
            val aarsloenn = BigDecimal(periode.månedsinntekt).multiply(BigDecimal(12))

            periode.dagsats = aarsloenn.min(seksG)
                .divide(BigDecimal(antallDager), 2, RoundingMode.HALF_UP)
                .toDouble()

            periode.belop =
                BigDecimal(periode.dagsats)
                    .multiply(BigDecimal(periode.antallDagerMedRefusjon))
                    .multiply(BigDecimal(periode.gradering))
                    .setScale(2, RoundingMode.HALF_UP)
                    .toDouble()
        }
    }
}
