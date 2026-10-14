package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.url
import io.mockk.every
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import no.nav.helse.fritakagp.FritakAgpApplication
import no.nav.helse.fritakagp.Issuers
import no.nav.helse.fritakagp.db.createHikariConfig
import no.nav.helse.slowtests.utils.WithPostgresContainer
import no.nav.helsearbeidsgiver.utils.test.mock.mockStatic
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.koin.test.KoinTest
import org.koin.test.inject

/**
Denne klassen kjører opp applikasjonen med Koin-profilen LOCAL slik at man kan
1) Kjøre tester mot HTTP-endepunktene slik de er i miljøene (Q+P)
2) Kjøre tester mot systemet (bakgrunnsjobber feks) mens de er realistisk  konfigurert
3) Kjøre ende til ende-tester (feks teste at en søknad send inn på HTTP-endepunktet havner i databasen riktig)
*/
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class SystemTestBase :
    WithPostgresContainer(),
    KoinTest {
    val httpClient by inject<HttpClient>()

    private val mockOAuth2Server = MockOAuth2Server().apply { start(6666) }
    private val serverPort = 8989
    private val app =
        mockStatic(::createHikariConfig) {
            every { createHikariConfig(any(), any(), any()) } returns hikariConfig

            FritakAgpApplication(
                port = serverPort,
                runAsDaemon = false,
            )
        }

    @AfterAll
    fun after() {
        mockOAuth2Server.shutdown()
        app.shutdown()
    }

    /** Hjelpefunksjon for at JUnit5 skal kunne kjenne igjen tester som kaller "suspend"-funksjoner */
    fun suspendableTest(block: suspend CoroutineScope.() -> Unit) {
        runBlocking {
            block()
        }
    }

    /** Hjelpefunksjon for å kalle HTTP-endepunktene med riktig port i testene */
    fun HttpRequestBuilder.appUrl(relativePath: String) {
        url("http://localhost:$serverPort$relativePath")
    }

    /** Hjelpefunksjon for å hente ut gyldig JWT-token og legge det til som Auth-header på en request */
    fun HttpRequestBuilder.loggedInAs(subject: String) {
        val authToken =
            mockOAuth2Server
                .issueToken(
                    issuerId = Issuers.TOKENX,
                    subject = subject,
                    audience = "aud-localhost",
                    claims =
                        mapOf(
                            "pid" to subject,
                        ),
                ).serialize()

        bearerAuth(authToken)
    }
}
