package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helse.GravidTestData
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.domain.FravaerData
import no.nav.helse.fritakagp.domain.KroniskSoeknad
import no.nav.helse.fritakagp.web.api.resreq.KroniskSoknadRequest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.koin.test.inject

class KroniskSoeknadHTTPTests : SystemTestBase() {
    private val soeknadKroniskUrl = "/fritak-agp-api/api/v1/kronisk/soeknad"

    @Test
    fun `Returnerer søknaden når korrekt bruker er innlogget, 404 når ikke`() = suspendableTest {
        val repo by inject<KroniskSoeknadRepository>()

        repo.insert(KroniskTestData.soeknadKronisk)
        val response =
            httpClient.get {
                appUrl("$soeknadKroniskUrl/${KroniskTestData.soeknadKronisk.id}")
                contentType(ContentType.Application.Json)
                loggedInAs("12345678910")
            }

        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)

        val accessGrantedForm = httpClient.get {
            appUrl("$soeknadKroniskUrl/${KroniskTestData.soeknadKronisk.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(KroniskTestData.soeknadKronisk.identitetsnummer)
        }.body<KroniskSoeknad>()

        Assertions.assertThat(accessGrantedForm).isEqualToIgnoringGivenFields(KroniskTestData.soeknadKronisk, "referansenummer")
    }

    @Test
    fun `invalid enum fields gives 400 Bad request`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(soeknadKroniskUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(KroniskTestData.validIdentitetsnummer)

                setBody(
                    """
                    {
                        "fnr": "${GravidTestData.validIdentitetsnummer}",
                        "orgnr": "${GravidTestData.fullValidSoeknadRequest.virksomhetsnummer}",
                        "bekreftelse": true,
                    }
                    """.trimIndent()
                )
            }

        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `Skal returnere Created ved feilfritt skjema uten fil`() = suspendableTest {
        val response = httpClient.post {
            appUrl(soeknadKroniskUrl)
            contentType(ContentType.Application.Json)
            loggedInAs(KroniskTestData.validIdentitetsnummer)
            setBody(KroniskTestData.fullValidRequest)
        }

        val soeknad = response.body<KroniskSoeknad>()
        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        Assertions.assertThat(soeknad.virksomhetsnummer).isEqualTo(KroniskTestData.fullValidRequest.virksomhetsnummer)
    }

    @Test
    fun `Skal validere feil ved ugyldig data`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(soeknadKroniskUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(KroniskTestData.validIdentitetsnummer)
                setBody(
                    KroniskSoknadRequest(
                        virksomhetsnummer = "lkajsbdfv",
                        identitetsnummer = "lkdf",
                        antallPerioder = 0,
                        fravaer = setOf(FravaerData("2001-01", 12F)),
                        bekreftet = true,
                        dokumentasjon = null,
                        ikkeHistoriskFravaer = false
                    )
                )
            }
        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
    }

    @Test
    fun `Skal returnere Created ved gyldig data (ikke historisk fravær)`() = suspendableTest {
        val response = httpClient.post {
            appUrl(soeknadKroniskUrl)
            contentType(ContentType.Application.Json)
            loggedInAs(KroniskTestData.validIdentitetsnummer)
            setBody(
                KroniskTestData.fullValidRequest.copy(
                    ikkeHistoriskFravaer = true,
                    fravaer = setOf(),
                    antallPerioder = 0
                )
            )
        }

        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.Created)
    }

    @Test
    fun `Skal returnere Created når fil er vedlagt`() = suspendableTest {
        val response = httpClient.post {
            appUrl(soeknadKroniskUrl)
            contentType(ContentType.Application.Json)
            loggedInAs(KroniskTestData.validIdentitetsnummer)
            setBody(KroniskTestData.kroniskSoknadMedFil)
        }

        val soeknad = response.body<KroniskSoeknad>()
        Assertions.assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        Assertions.assertThat(soeknad.harVedlegg).isEqualTo(true)
    }
}
