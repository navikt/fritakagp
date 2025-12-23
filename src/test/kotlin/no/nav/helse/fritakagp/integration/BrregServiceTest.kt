package no.nav.helse.fritakagp.integration

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BrregServiceTest {
    val mockBrregClient = mockk<BrregClient>()
    val brregService = BrregService(mockBrregClient)

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `hent navn`() {
        val orgnr = Orgnr.genererGyldig()
        val orgNavn = "Brønnøysundregistrene"

        coEvery { mockBrregClient.hentOrganisasjonNavn(setOf(orgnr.verdi)) } returns mapOf(orgnr to orgNavn)

        val resultat = runBlocking { brregService.hentOrganisasjonNavn(orgnr.verdi) }

        assertEquals(orgNavn, resultat)
    }

    @Test
    fun `svar med default om navn ikke finnes`() {
        val orgnr = Orgnr.genererGyldig().verdi

        coEvery { mockBrregClient.hentOrganisasjonNavn(setOf(orgnr)) } returns emptyMap()

        val resultat = runBlocking { brregService.hentOrganisasjonNavn(orgnr) }

        assertEquals("Ukjent arbeidsgiver", resultat)
    }

    @Test
    fun `bekreft eksistens av organisasjon`() {
        val orgnr = Orgnr.genererGyldig().verdi

        coEvery { mockBrregClient.erOrganisasjon(orgnr) } returns true

        val resultat = runBlocking { brregService.erOrganisasjon(orgnr) }

        assertTrue(resultat)
    }

    @Test
    fun `avkreft eksistens av organisasjon`() {
        val orgnr = Orgnr.genererGyldig().verdi

        coEvery { mockBrregClient.erOrganisasjon(orgnr) } returns false

        val resultat = runBlocking { brregService.erOrganisasjon(orgnr) }

        assertFalse(resultat)
    }
}
