package no.nav.helse.fritakagp.domain

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDate

data class Arbeidsgiverperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val antallDagerMedRefusjon: Int,
    val månedsinntekt: Double,
    val gradering: Double = 1.0
) {
    var dagsats: Double = 0.0
    var belop: Double = 0.0

    @get:JsonProperty(access = JsonProperty.Access.READ_ONLY)
    val graderingProsent: Double
        get() = gradering * 100
}
