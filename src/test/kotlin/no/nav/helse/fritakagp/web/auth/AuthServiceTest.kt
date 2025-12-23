package no.nav.helse.fritakagp.web.auth

import io.ktor.server.routing.RoutingContext
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.helsearbeidsgiver.altinn.AltinnTilgang
import no.nav.helsearbeidsgiver.altinn.AltinnTilgangRespons
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class AuthServiceTest {

    val mockFnr = "mock-fnr"
    val mockGetTokenFn = { "mock-token" }

    val mockAuthClient = mockk<AuthClient>()
    val mockAltinnClient = mockk<Altinn3OBOClient>()
    val mockContext = mockk<RoutingContext>(relaxed = true)

    val authService = AuthService(mockAuthClient, mockAltinnClient, "mock-scope")

    @BeforeEach
    fun setup() {
        mockkStatic(RoutingContext::hentFnrFraLoginToken)

        every { mockContext.hentFnrFraLoginToken() } returns mockFnr

        every { mockAuthClient.fetchOboToken(any(), any()) } returns mockGetTokenFn
    }

    @AfterEach
    fun teardown() {
        clearAllMocks()
    }

    @Test
    fun `bekrefter tilgang til organisasjon`() {
        val mockOrgnr = Orgnr.genererGyldig().verdi

        coEvery { mockAltinnClient.harTilgangTilOrganisasjon(mockFnr, mockOrgnr, mockGetTokenFn) } returns true

        assertDoesNotThrow {
            runBlocking {
                authService.validerTilgangTilOrganisasjon(mockContext, mockOrgnr)
            }
        }
    }

    @Test
    fun `avkrefter tilgang til organisasjon`() {
        val mockOrgnr = Orgnr.genererGyldig().verdi

        coEvery { mockAltinnClient.harTilgangTilOrganisasjon(mockFnr, mockOrgnr, mockGetTokenFn) } returns false

        assertThrows<ManglerAltinnRettigheterException> {
            runBlocking {
                authService.validerTilgangTilOrganisasjon(mockContext, mockOrgnr)
            }
        }
    }

    @Test
    fun `henter hierarki med tilganger`() {
        val mockOrgnr = Orgnr.genererGyldig().verdi
        val forventetHierarki = listOf(
            AltinnTilgang(
                orgnr = mockOrgnr,
                altinn3Tilganger = setOf("mock-tilgang-slottet"),
                altinn2Tilganger = setOf("mock-tilgang-skaugum"),
                underenheter = emptyList(),
                navn = "Hans Kongelige Høyhet",
                organisasjonsform = "Monarki"
            )
        )

        coEvery { mockAltinnClient.hentHierarkiMedTilganger(mockFnr, mockGetTokenFn) } returns AltinnTilgangRespons(
            isError = false,
            hierarki = forventetHierarki,
            tilgangTilOrgNr = emptyMap()
        )

        val hentetHierarki =
            runBlocking {
                authService.hentHierarkiMedTilganger(mockContext)
            }

        assertEquals(forventetHierarki, hentetHierarki)
    }
}
