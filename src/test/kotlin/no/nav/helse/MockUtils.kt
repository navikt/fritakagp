package no.nav.helse

import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.web.api.resreq.ArbeidsgiverperiodeRequest
import no.nav.helsearbeidsgiver.utils.test.date.januar

fun mockArbeidsgiverperiode(): Arbeidsgiverperiode =
    Arbeidsgiverperiode(
        fom = 5.januar(2020),
        tom = 10.januar(2020),
        antallDagerMedRefusjon = 5,
        månedsinntekt = 2590.8,
        gradering = 1.0,
        dagsats = 7772.4,
        belop = 38862.0
    )

fun mockArbeidsgiverperiodeRequest(): ArbeidsgiverperiodeRequest {
    val agp = mockArbeidsgiverperiode()
    return ArbeidsgiverperiodeRequest(
        fom = agp.fom,
        tom = agp.tom,
        antallDagerMedRefusjon = agp.antallDagerMedRefusjon,
        månedsinntekt = agp.månedsinntekt,
        gradering = agp.gradering
    )
}
