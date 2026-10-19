package no.nav.helse.fritakagp.processing.brukernotifikasjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class BrukernotifikasjonJobbdataTest {
    @Test
    fun `test hentTekst for Oppretting`() {
        val jobData =
            BrukernotifikasjonJobbdata(
                skjemaId = UUID.randomUUID(),
                identitetsnummer = "20015001543",
                virksomhetsnavn = "Bedrift",
                skjemaType = BrukernotifikasjonJobbdata.SkjemaType.KroniskKrav,
                notifikasjonsType = BrukernotifikasjonJobbdata.NotifikasjonsType.Oppretting,
            )
        val expectedText = "Bedrift har søkt om at NAV dekker sykepenger fra første dag av sykefraværet ditt."
        assertEquals(expectedText, jobData.hentTekst())
    }

    @Test
    fun `test hentTekst for Annullering`() {
        val jobData =
            BrukernotifikasjonJobbdata(
                skjemaId = UUID.randomUUID(),
                identitetsnummer = "20015001543",
                virksomhetsnavn = "Bedrift",
                skjemaType = BrukernotifikasjonJobbdata.SkjemaType.KroniskKrav,
                notifikasjonsType = BrukernotifikasjonJobbdata.NotifikasjonsType.Annullering,
            )
        val expectedText = "Bedrift har trukket kravet om at NAV dekker sykepenger fra første dag av sykefraværet ditt."
        assertEquals(expectedText, jobData.hentTekst())
    }

    @Test
    fun `test hentLenke for KroniskKrav Oppretting`() {
        val skjemaId = UUID.randomUUID()
        val jobData =
            BrukernotifikasjonJobbdata(
                skjemaId = skjemaId,
                identitetsnummer = "20015001543",
                virksomhetsnavn = "Bedrift",
                skjemaType = BrukernotifikasjonJobbdata.SkjemaType.KroniskKrav,
                notifikasjonsType = BrukernotifikasjonJobbdata.NotifikasjonsType.Oppretting,
            )
        val expectedLink = "/nb/notifikasjon/kronisk/krav/$skjemaId"
        assertEquals(expectedLink, jobData.hentLenke())
    }

    @Test
    fun `test hentLenke for KroniskKrav Annullering`() {
        val skjemaId = UUID.randomUUID()
        val jobData =
            BrukernotifikasjonJobbdata(
                skjemaId = skjemaId,
                identitetsnummer = "20015001543",
                virksomhetsnavn = "Bedrift",
                skjemaType = BrukernotifikasjonJobbdata.SkjemaType.KroniskKrav,
                notifikasjonsType = BrukernotifikasjonJobbdata.NotifikasjonsType.Annullering,
            )
        val expectedLink = "/nb/notifikasjon/kronisk/krav/slettet/$skjemaId"
        assertEquals(expectedLink, jobData.hentLenke())
    }

    @Test
    fun `test hentLenke for GravidKrav Oppretting`() {
        val skjemaId = UUID.randomUUID()
        val jobData =
            BrukernotifikasjonJobbdata(
                skjemaId = skjemaId,
                identitetsnummer = "20015001543",
                virksomhetsnavn = "Bedrift",
                skjemaType = BrukernotifikasjonJobbdata.SkjemaType.GravidKrav,
                notifikasjonsType = BrukernotifikasjonJobbdata.NotifikasjonsType.Oppretting,
            )
        val expectedLink = "/nb/notifikasjon/gravid/krav/$skjemaId"
        assertEquals(expectedLink, jobData.hentLenke())
    }

    @Test
    fun `test hentLenke for GravidKrav Annullering`() {
        val skjemaId = UUID.randomUUID()
        val jobData =
            BrukernotifikasjonJobbdata(
                skjemaId = skjemaId,
                identitetsnummer = "20015001543",
                virksomhetsnavn = "Bedrift",
                skjemaType = BrukernotifikasjonJobbdata.SkjemaType.GravidKrav,
                notifikasjonsType = BrukernotifikasjonJobbdata.NotifikasjonsType.Annullering,
            )
        val expectedLink = "/nb/notifikasjon/gravid/krav/slettet/$skjemaId"
        assertEquals(expectedLink, jobData.hentLenke())
    }
}
