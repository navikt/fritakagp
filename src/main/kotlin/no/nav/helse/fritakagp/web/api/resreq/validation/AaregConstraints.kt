package no.nav.helse.fritakagp.web.api.resreq.validation

import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import org.valiktor.Validator
import java.time.LocalDate
import no.nav.helsearbeidsgiver.aareg.Periode as AaregPeriode

class ArbeidsforholdConstraint : CustomConstraint

private const val MAKS_DAGER_OPPHOLD = 3L

fun <E> Validator<E>.Property<LocalDate?>.maaHaAktivAnsettelsesperiode(agp: Arbeidsgiverperiode, ansettelsesperioder: Set<AaregPeriode>) =
    this.validate(ArbeidsforholdConstraint()) {
        val ansattPerioder = slaaSammenPerioder(ansettelsesperioder)
        return@validate agp.innenforArbeidsforhold(ansattPerioder) ||
            agp.innenforArbeidsforhold(ansettelsesperioder)
    }

fun Arbeidsgiverperiode.innenforArbeidsforhold(ansattPerioder: Set<AaregPeriode>): Boolean =
    ansattPerioder.any { ansPeriode ->
        !fom.isBefore(ansPeriode.fom) &&
            (ansPeriode.tom == null || !tom.isAfter(ansPeriode.tom))
    }

fun slaaSammenPerioder(ansettelsesperioder: Set<AaregPeriode>): Set<AaregPeriode> {
    if (ansettelsesperioder.size <= 1) return ansettelsesperioder

    val remainingPeriods = ansettelsesperioder
        .sortedBy { it.fom }
        .toMutableList()

    val merged = ArrayList<AaregPeriode>()

    do {
        var currentPeriod = remainingPeriods[0]
        remainingPeriods.removeAt(0)

        do {
            val connectedPeriod = remainingPeriods
                .find { !oppholdMellomPerioderOverstigerDager(currentPeriod, it) }
            if (connectedPeriod != null) {
                currentPeriod = AaregPeriode(currentPeriod.fom, connectedPeriod.tom)
                remainingPeriods.remove(connectedPeriod)
            }
        } while (connectedPeriod != null)

        merged.add(currentPeriod)
    } while (remainingPeriods.isNotEmpty())

    return merged.toSet()
}

fun oppholdMellomPerioderOverstigerDager(
    a1: AaregPeriode,
    a2: AaregPeriode
): Boolean {
    return a1.tom?.plusDays(MAKS_DAGER_OPPHOLD)?.isBefore(a2.fom) ?: true
}
