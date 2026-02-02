package no.nav.helse.fritakagp.domain

import java.time.LocalDate

data class Arbeidsgiverperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val antallDagerMedRefusjon: Int,
    val månedsinntekt: Double,
    val gradering: Double = 1.0,
    val dagsats: Double,
    val belop: Double
)
