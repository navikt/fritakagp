package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helse.GravidTestData
import no.nav.helse.KroniskTestData
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthenticationTests : SystemTestBase() {
    private val soeknadGravidUrl = "/fritak-agp-api/api/v1/gravid/soeknad"

    @Test
    fun `posting application with no JWT returns 401 Unauthorized`() =
        suspendableTest {
            val response =
                httpClient.post {
                    appUrl(soeknadGravidUrl)
                    contentType(ContentType.Application.Json)
                    setBody(GravidTestData.fullValidSoeknadRequest)
                }

            assertThat(response.status).isEqualTo(HttpStatusCode.Unauthorized)
        }

    @Test
    fun `posting application with Valid JWT does not return 401 Unauthorized`() =
        suspendableTest {
            val response =
                httpClient.post {
                    appUrl(soeknadGravidUrl)
                    contentType(ContentType.Application.Json)
                    loggedInAs(KroniskTestData.VALID_IDENTITETSNUMMER)

                    setBody(GravidTestData.fullValidSoeknadRequest)
                }

            assertThat(response.status).isNotEqualTo(HttpStatusCode.Unauthorized)
        }
}
