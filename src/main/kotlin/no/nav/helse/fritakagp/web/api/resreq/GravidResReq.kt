package no.nav.helse.fritakagp.web.api.resreq

import no.nav.helse.fritakagp.domain.AarsakEndring
import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.GravidSoeknad
import no.nav.helse.fritakagp.domain.Omplassering
import no.nav.helse.fritakagp.domain.OmplasseringAarsak
import no.nav.helse.fritakagp.domain.Tiltak
import no.nav.helse.fritakagp.web.api.resreq.validation.isAvStorrelse
import no.nav.helse.fritakagp.web.api.resreq.validation.isGodkjentFiltype
import no.nav.helse.fritakagp.web.api.resreq.validation.isValidIdentitetsnummer
import no.nav.helse.fritakagp.web.api.resreq.validation.isValidOrganisasjonsnummer
import no.nav.helse.fritakagp.web.api.resreq.validation.isVirksomhet
import no.nav.helsearbeidsgiver.aareg.Periode
import org.valiktor.functions.isGreaterThan
import org.valiktor.functions.isLessThanOrEqualTo
import org.valiktor.functions.isNotEmpty
import org.valiktor.functions.isNotNull
import org.valiktor.functions.isTrue
import org.valiktor.functions.validateForEach
import org.valiktor.validate
import java.time.LocalDate

data class GravidSoknadRequest(
    val virksomhetsnummer: String,
    val identitetsnummer: String,
    val tilrettelegge: Boolean,
    val termindato: LocalDate?,

    val tiltak: List<Tiltak>? = null,
    val tiltakBeskrivelse: String? = null,

    val omplassering: Omplassering? = null,
    val omplasseringAarsak: OmplasseringAarsak? = null,
    val bekreftet: Boolean,

    val dokumentasjon: String?
) {

    fun validate(isVirksomhet: Boolean) {
        validate(this) {
            validate(GravidSoknadRequest::identitetsnummer).isValidIdentitetsnummer()
            validate(GravidSoknadRequest::bekreftet).isTrue()
            validate(GravidSoknadRequest::virksomhetsnummer).isValidOrganisasjonsnummer()
            validate(GravidSoknadRequest::virksomhetsnummer).isVirksomhet(isVirksomhet)

            if (this@GravidSoknadRequest.tilrettelegge) {
                validate(GravidSoknadRequest::tiltak).isNotNull()

                if (this@GravidSoknadRequest.tiltak?.contains(Tiltak.ANNET) == true) {
                    validate(GravidSoknadRequest::tiltakBeskrivelse).isNotNull()
                    validate(GravidSoknadRequest::tiltakBeskrivelse).isNotEmpty()
                }

                if (this@GravidSoknadRequest.omplassering == Omplassering.IKKE_MULIG) {
                    validate(GravidSoknadRequest::omplasseringAarsak).isNotNull()
                }
            }

            if (!this@GravidSoknadRequest.dokumentasjon.isNullOrEmpty()) {
                validate(GravidSoknadRequest::dokumentasjon).isGodkjentFiltype()
                validate(GravidSoknadRequest::dokumentasjon).isAvStorrelse(SMALLEST_PDF_SIZE, 10L * MB)
            }
        }
    }

    fun toDomain(sendtAv: String, sendtAvNavn: String, navn: String) = GravidSoeknad(
        virksomhetsnummer = virksomhetsnummer,
        identitetsnummer = identitetsnummer,
        navn = navn,
        sendtAv = sendtAv,
        sendtAvNavn = sendtAvNavn,
        termindato = termindato,
        omplassering = omplassering,
        omplasseringAarsak = omplasseringAarsak,
        tilrettelegge = tilrettelegge,
        tiltak = tiltak,
        tiltakBeskrivelse = tiltakBeskrivelse,
        harVedlegg = !dokumentasjon.isNullOrEmpty()
    )
}

data class GravidKravRequest(
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
            validate(GravidKravRequest::antallDager).isGreaterThan(0)
            validate(GravidKravRequest::antallDager).isLessThanOrEqualTo(366)
            validate(GravidKravRequest::identitetsnummer).isValidIdentitetsnummer()
            validate(GravidKravRequest::virksomhetsnummer).isValidOrganisasjonsnummer()
            validate(GravidKravRequest::bekreftet).isTrue()
            validate(GravidKravRequest::perioder).validateForEach { it.validate(this, ansettelsesperioder) }
        }
    }

    fun tilKrav(sendtAv: String, sendtAvNavn: String, navn: String, perioder: List<Arbeidsgiverperiode>) = GravidKrav(
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

const val MB = 1024 * 1024
const val SMALLEST_PDF_SIZE = 500L // https://nav-it.slack.com/archives/GUX7MEP4J/p1648123662006049
