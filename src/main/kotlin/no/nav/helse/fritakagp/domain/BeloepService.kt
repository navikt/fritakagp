package no.nav.helse.fritakagp.domain

import no.nav.helse.fritakagp.divideSafe
import no.nav.helse.fritakagp.integration.GrunnbeloepClient
import no.nav.helse.fritakagp.multiplySafe
import no.nav.helse.fritakagp.web.api.resreq.ArbeidsgiverperiodeRequest
import no.nav.helse.fritakagp.web.api.resreq.GravidKravRequest
import no.nav.helse.fritakagp.web.api.resreq.KroniskKravRequest
import java.math.RoundingMode

class BeloepService(
    private val grunnbeloepClient: GrunnbeloepClient
) {
    fun perioderMedDagsatsOgBeloep(request: KroniskKravRequest): List<Arbeidsgiverperiode> = request.perioder.medDagsatsOgBeloep(request.antallDager)

    fun perioderMedDagsatsOgBeloep(request: GravidKravRequest): List<Arbeidsgiverperiode> = request.perioder.medDagsatsOgBeloep(request.antallDager)

    private fun List<ArbeidsgiverperiodeRequest>.medDagsatsOgBeloep(antallDager: Int): List<Arbeidsgiverperiode> =
        map { periode ->
            val grunnbeloep = grunnbeloepClient.hentGrunnbeloep(periode.fom)
            val seksG = grunnbeloep.multiplySafe(6)
            val aarsloenn = periode.månedsinntekt.multiplySafe(12)

            val dagsats = aarsloenn.min(seksG)
                .divideSafe(antallDager)
                .toDouble()

            val beloep =
                dagsats
                    .multiplySafe(periode.antallDagerMedRefusjon)
                    .multiplySafe(periode.gradering)
                    .setScale(2, RoundingMode.HALF_UP)
                    .toDouble()

            Arbeidsgiverperiode(
                fom = periode.fom,
                tom = periode.tom,
                antallDagerMedRefusjon = periode.antallDagerMedRefusjon,
                månedsinntekt = periode.månedsinntekt,
                gradering = periode.gradering,
                dagsats = dagsats,
                belop = beloep
            )
        }
}
