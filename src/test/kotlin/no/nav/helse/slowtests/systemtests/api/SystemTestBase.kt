package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import no.nav.helse.fritakagp.FritakAgpApplication
import no.nav.helse.fritakagp.web.api.resreq.validation.Problem
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.koin.test.KoinTest
import org.koin.test.inject

@TestInstance(TestInstance.Lifecycle.PER_CLASS)

/**
 * Denne klassen kjører opp applikasjonen med Koin-profilen LOCAL
 * slik at man kan
 * 1) Kjøre tester mot HTTP-endepunktene slik de er i miljøene (Q+P)
 * 2) Kjøre tester mot systemet (bakgrunnsjobber feks) mens de er realistisk  konfigurert
 * 3) Kjøre ende til ende-tester (feks teste at en søknad send inn på HTTP-endepunktet havner i databasen riktig)
 */
open class SystemTestBase : KoinTest {

    val httpClient by inject<HttpClient>()

    companion object {
        const val testServerPort = 8989
        var app: FritakAgpApplication? = null
    }

    @BeforeAll
    fun before() {
        if (app == null) {
            app = FritakAgpApplication(port = testServerPort, false)
            Thread.sleep(200)
        }
    }

    /**
     * Hjelpefunksjon for å kalle HTTP-endepunktene med riktig port i testene
     */
    fun HttpRequestBuilder.appUrl(relativePath: String) {
        url("http://localhost:$testServerPort$relativePath")
    }

    /**
     * Hjelpefunksjon for å hente ut gyldig JWT-token og legge det til som Auth header på en request
     */
    suspend fun HttpRequestBuilder.loggedInAs(subject: String) {
        val response = httpClient.get {
            appUrl("/local/token-please?subject=$subject")
            contentType(ContentType.Application.Json)
        }.body<String>()

        header("Authorization", "Bearer $response")
    }

    /**
     * Hjelpefunksjon for at JUnit5 skal kunne kjenne igjen tester som kaller har "suspend"-funksjoner
     */
    fun suspendableTest(block: suspend CoroutineScope.() -> Unit) {
        runBlocking {
            block()
        }
    }

    suspend fun extractResponseBody(response: HttpResponse) =
        response.call.response.body<Problem>()
}
