package no.nav.helse.fritakagp.web.api.resreq

import no.nav.helse.fritakagp.domain.AarsakEndring
import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.domain.FravaerData
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.domain.KroniskSoeknad
import no.nav.helse.fritakagp.web.api.resreq.validation.ikkeFlereFravaersdagerEnnDagerIMaanden
import no.nav.helse.fritakagp.web.api.resreq.validation.ingenDataEldreEnn
import no.nav.helse.fritakagp.web.api.resreq.validation.ingenDataFraFremtiden
import no.nav.helse.fritakagp.web.api.resreq.validation.isAvStorrelse
import no.nav.helse.fritakagp.web.api.resreq.validation.isGodkjentFiltype
import no.nav.helse.fritakagp.web.api.resreq.validation.isValidIdentitetsnummer
import no.nav.helse.fritakagp.web.api.resreq.validation.isValidOrganisasjonsnummer
import no.nav.helse.fritakagp.web.api.resreq.validation.isVirksomhet
import no.nav.helsearbeidsgiver.aareg.Periode
import org.valiktor.functions.isBetween
import org.valiktor.functions.isEmpty
import org.valiktor.functions.isEqualTo
import org.valiktor.functions.isGreaterThan
import org.valiktor.functions.isLessThanOrEqualTo
import org.valiktor.functions.isNotNull
import org.valiktor.functions.isTrue
import org.valiktor.functions.validateForEach
import org.valiktor.validate

data class KroniskSoknadRequest(
    val virksomhetsnummer: String,
    val identitetsnummer: String,
    val ikkeHistoriskFravaer: Boolean,
    val fravaer: Set<FravaerData>,
    val bekreftet: Boolean,
    val antallPerioder: Int,

    val dokumentasjon: String?
) {

    fun validate(isVirksomhet: Boolean) {
        validate(this) {
            validate(KroniskSoknadRequest::identitetsnummer).isValidIdentitetsnummer()
            validate(KroniskSoknadRequest::bekreftet).isTrue()
            validate(KroniskSoknadRequest::virksomhetsnummer).isValidOrganisasjonsnummer()
            validate(KroniskSoknadRequest::virksomhetsnummer).isVirksomhet(isVirksomhet)

            if (this@KroniskSoknadRequest.ikkeHistoriskFravaer) {
                validate(KroniskSoknadRequest::fravaer).isEmpty()
                validate(KroniskSoknadRequest::antallPerioder).isEqualTo(0)
            } else {
                validate(KroniskSoknadRequest::fravaer).isNotNull()
                validate(KroniskSoknadRequest::antallPerioder).isBetween(1, 300)
                validate(KroniskSoknadRequest::fravaer).ingenDataEldreEnn(2)
                validate(KroniskSoknadRequest::fravaer).ingenDataFraFremtiden()
                validate(KroniskSoknadRequest::fravaer).ikkeFlereFravaersdagerEnnDagerIMaanden()
            }

            if (!this@KroniskSoknadRequest.dokumentasjon.isNullOrEmpty()) {
                validate(KroniskSoknadRequest::dokumentasjon).isGodkjentFiltype()
                validate(KroniskSoknadRequest::dokumentasjon).isAvStorrelse(SMALLEST_PDF_SIZE, 10L * MB)
            }
        }
    }

    fun toDomain(sendtAv: String, sendtAvNavn: String, navn: String) = KroniskSoeknad(
        virksomhetsnummer = virksomhetsnummer,
        identitetsnummer = identitetsnummer,
        navn = navn,
        sendtAv = sendtAv,
        sendtAvNavn = sendtAvNavn,
        antallPerioder = antallPerioder,
        fravaer = fravaer,
        ikkeHistoriskFravaer = ikkeHistoriskFravaer,
        bekreftet = bekreftet,
        harVedlegg = !dokumentasjon.isNullOrEmpty()
    )
}

data class KroniskKravRequest(
    val virksomhetsnummer: String,
    val identitetsnummer: String,
    val perioder: List<ArbeidsgiverperiodeRequest>,
    val bekreftet: Boolean,
    val kontrollDager: Int?,
    val antallDager: Int,
    var aarsakEndring: AarsakEndring? = null
) {
    fun validate(ansettelsesperioder: Set<Periode>) {
        validate(this) {
            validate(KroniskKravRequest::antallDager).isGreaterThan(0)
            validate(KroniskKravRequest::antallDager).isLessThanOrEqualTo(366)
            validate(KroniskKravRequest::identitetsnummer).isValidIdentitetsnummer()
            validate(KroniskKravRequest::virksomhetsnummer).isValidOrganisasjonsnummer()
            validate(KroniskKravRequest::bekreftet).isTrue()
            validate(KroniskKravRequest::perioder).validateForEach { it.validate(this, ansettelsesperioder) }
        }
    }

    fun tilKrav(sendtAv: String, sendtAvNavn: String, navn: String, perioder: List<Arbeidsgiverperiode>) = KroniskKrav(
        identitetsnummer = identitetsnummer,
        navn = navn,
        virksomhetsnummer = virksomhetsnummer,
        perioder = perioder,
        sendtAv = sendtAv,
        sendtAvNavn = sendtAvNavn,
        kontrollDager = kontrollDager,
        antallDager = antallDager,
        aarsakEndring = aarsakEndring?.name
    )
}
