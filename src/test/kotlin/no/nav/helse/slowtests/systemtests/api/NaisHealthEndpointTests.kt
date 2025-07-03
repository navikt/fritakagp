package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NaisHealthEndpointTests : SystemTestBase() {

    @Test
    fun `nais isalive endpoint with no JWT returns ProbeResult OK`() = suspendableTest {
        val response = httpClient.get {
            appUrl("/health/alive")
        }

        Assertions.assertThat(response.bodyAsText()).isNotBlank()
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `nais isready endpoint with no JWT returns 200 OK`() = suspendableTest {
        val response = httpClient.get {
            appUrl("/health/ready")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        Assertions.assertThat(response.bodyAsText()).isNotBlank()
    }
}
