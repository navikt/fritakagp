package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.request.delete
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.db.GravidKravRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.koin.test.inject
import java.util.UUID

class SlettGravidKravHTTPTests : SystemTestBase() {
    private val kravGravidUrl = "/fritak-agp-api/api/v1/gravid/krav"

    @Test
    fun `Skal returnere 200 OK når vi sletter med korrekt bruker innlogget`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        repo.insert(GravidTestData.gravidKrav)

        val response = httpClient.delete {
            appUrl("$kravGravidUrl/${GravidTestData.gravidKrav.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(GravidTestData.gravidKrav.identitetsnummer)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `Skal returnere 404 når kravet ikke finnes`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        repo.insert(GravidTestData.gravidKrav)

        val response =
            httpClient.delete {
                appUrl("$kravGravidUrl/${UUID.randomUUID()}")
                contentType(ContentType.Application.Json)
                loggedInAs(GravidTestData.gravidKrav.identitetsnummer)
            }
        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `Skal returnere forbidden hvis virksomheten ikke er i auth listen fra altinn`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        val id = UUID.randomUUID()

        repo.insert(GravidTestData.gravidKrav.copy(virksomhetsnummer = "123456785", id = id))
        val response =
            httpClient.delete {
                appUrl("$kravGravidUrl/$id")
                contentType(ContentType.Application.Json)
                loggedInAs("12345678910")
            }
        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
    }
}
