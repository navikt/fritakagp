package no.nav.helse.fritakagp.web.api.resreq

import no.nav.helse.fritakagp.web.api.resreq.validation.datoerHarRiktigRekkefolge
import no.nav.helse.fritakagp.web.api.resreq.validation.maaHaAktivAnsettelsesperiode
import no.nav.helse.fritakagp.web.api.resreq.validation.maanedsInntektErMellomNullOgTiMil
import no.nav.helse.fritakagp.web.api.resreq.validation.refusjonsDagerIkkeOverstigerPeriodelengde
import no.nav.helsearbeidsgiver.aareg.Periode
import org.valiktor.Validator
import org.valiktor.functions.isGreaterThanOrEqualTo
import org.valiktor.functions.isLessThanOrEqualTo
import java.time.LocalDate

data class ArbeidsgiverperiodeRequest(
    val fom: LocalDate,
    val tom: LocalDate,
    val antallDagerMedRefusjon: Int,
    val månedsinntekt: Double,
    val gradering: Double = 1.0
) {
    fun validate(v: Validator<ArbeidsgiverperiodeRequest>, ansettelsesperioder: Set<Periode>) {
        v.validate(ArbeidsgiverperiodeRequest::fom).datoerHarRiktigRekkefolge(tom)
        v.validate(ArbeidsgiverperiodeRequest::antallDagerMedRefusjon).refusjonsDagerIkkeOverstigerPeriodelengde(this)
        v.validate(ArbeidsgiverperiodeRequest::månedsinntekt).maanedsInntektErMellomNullOgTiMil()
        v.validate(ArbeidsgiverperiodeRequest::fom).maaHaAktivAnsettelsesperiode(this, ansettelsesperioder)
        v.validate(ArbeidsgiverperiodeRequest::gradering).isLessThanOrEqualTo(1.0)
        v.validate(ArbeidsgiverperiodeRequest::gradering).isGreaterThanOrEqualTo(0.2)
    }
}
