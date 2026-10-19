package no.nav.helse.fritakagp.processing.brukernotifikasjon

import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Annullering
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Endring
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Oppretting
import java.util.UUID

private const val ENDEPUNKT_KRONISK = "/nb/notifikasjon/kronisk/"

private const val ENDEPUNKT_GRAVID = "/nb/notifikasjon/gravid/"

data class BrukernotifikasjonJobbdata(
    val skjemaId: UUID,
    val identitetsnummer: String,
    val virksomhetsnavn: String?,
    val skjemaType: SkjemaType,
    val notifikasjonsType: NotifikasjonsType = Oppretting,
) {
    fun hentTekst(): String {
        val ukjentArbeidsgiver = "Arbeidsgiveren din"
        return when (notifikasjonsType) {
            Oppretting, Endring -> "${virksomhetsnavn ?: ukjentArbeidsgiver} har søkt om at NAV dekker sykepenger fra første dag av sykefraværet ditt."
            Annullering -> "${virksomhetsnavn ?: ukjentArbeidsgiver} har trukket kravet om at NAV dekker sykepenger fra første dag av sykefraværet ditt."
        }
    }

    fun hentLenke(): String =
        when (skjemaType) {
            SkjemaType.KroniskKrav -> {
                when (notifikasjonsType) {
                    Oppretting, Endring -> "${ENDEPUNKT_KRONISK}krav/$skjemaId"
                    Annullering -> "${ENDEPUNKT_KRONISK}krav/slettet/$skjemaId"
                }
            }

            SkjemaType.GravidKrav -> {
                when (notifikasjonsType) {
                    Oppretting, Endring -> "${ENDEPUNKT_GRAVID}krav/$skjemaId"
                    Annullering -> "${ENDEPUNKT_GRAVID}krav/slettet/$skjemaId"
                }
            }

            SkjemaType.KroniskSøknad -> {
                "${ENDEPUNKT_KRONISK}soknad/$skjemaId"
            }

            SkjemaType.GravidSøknad -> {
                "${ENDEPUNKT_GRAVID}soknad/$skjemaId"
            }
        }

    enum class SkjemaType {
        KroniskKrav,
        KroniskSøknad,
        GravidKrav,
        GravidSøknad,
    }

    enum class NotifikasjonsType {
        Oppretting,
        Endring,
        Annullering,
    }
}
