package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.web.api.resreq.validation.ValidationProblem
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.koin.test.inject
import java.time.LocalDate

class KroniskKravHTTPTests : SystemTestBase() {
    private val kravKroniskUrl = "/fritak-agp-api/api/v1/kronisk/krav"

    @Test
    fun `Gir not found når kravet er slettet`() = suspendableTest {
        val repo by inject<KroniskKravRepository>()

        repo.insert(KroniskTestData.kroniskKrav.copy(status = KravStatus.SLETTET))

        val response = httpClient.get {
            appUrl("$kravKroniskUrl/${KroniskTestData.kroniskKrav.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(KroniskTestData.kroniskKrav.identitetsnummer)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `Gir ikke not found når kravet er slettet men flaget slettet er satt`() = suspendableTest {
        val repo by inject<KroniskKravRepository>()

        repo.insert(KroniskTestData.kroniskKrav.copy(status = KravStatus.SLETTET))

        val response = httpClient.get {
            appUrl("$kravKroniskUrl/${KroniskTestData.kroniskKrav.id}?slettet")
            contentType(ContentType.Application.Json)
            loggedInAs(KroniskTestData.kroniskKrav.identitetsnummer)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `invalid json gives 400 Bad request`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(kravKroniskUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(KroniskTestData.validIdentitetsnummer)

                setBody(
                    """
                    {
                        "identitetsnummer": "${KroniskTestData.validIdentitetsnummer}",
                        "virksomhetsnummer": "${KroniskTestData.fullValidRequest.virksomhetsnummer}",
                        "perioder": fele,
                        "bekreftet": ["IKKE GYLDIG"]
                        "dokumentasjon": ["IKKE GYLDIG"]
                        "kontrollDager": ["IKKE GYLDIG"]
                    }
                    """.trimIndent()
                )
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        val res = extractResponseBody(response)
        assertThat(res.title).contains("Valideringen av input feilet")
    }

    @Test
    fun `Skal returnere Created ved feilfritt skjema uten fil`() = suspendableTest {
        val response = httpClient.post {
            appUrl(kravKroniskUrl)
            contentType(ContentType.Application.Json)
            loggedInAs(KroniskTestData.validIdentitetsnummer)
            setBody(KroniskTestData.kroniskKravRequestValid)
        }

        val krav = response.body<KroniskKrav>()
        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        assertThat(krav.identitetsnummer).isEqualTo(KroniskTestData.kroniskKravRequestValid.identitetsnummer)
    }

    @Test
    fun `Skal returnere forbidden hvis virksomheten ikke er i auth listen fra altinn`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(kravKroniskUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(KroniskTestData.validIdentitetsnummer)
                setBody(KroniskTestData.kroniskKravRequestValid.copy(virksomhetsnummer = "123456785"))
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    fun `Skal returnere full propertypath for periode`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(kravKroniskUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(KroniskTestData.validIdentitetsnummer)
                setBody(
                    KroniskTestData.kroniskKravRequestInValid.copy(
                        perioder = listOf(
                            Arbeidsgiverperiode(
                                LocalDate.of(2020, 2, 1),
                                LocalDate.of(2020, 1, 31),
                                29,
                                månedsinntekt = 34000000.0
                            ),
                            Arbeidsgiverperiode(
                                LocalDate.of(2020, 2, 3),
                                LocalDate.of(2020, 1, 31),
                                23,
                                månedsinntekt = -30.0
                            ),
                            Arbeidsgiverperiode(
                                LocalDate.of(2020, 1, 5),
                                LocalDate.of(2020, 1, 14),
                                12,
                                månedsinntekt = 2590.8
                            )
                        )
                    )
                )
            }

        val possiblePropertyPaths = listOf(
            "perioder[0].fom",
            "perioder[0].månedsinntekt",
            "perioder[0].antallDagerMedRefusjon",
            "perioder[1].fom",
            "perioder[1].antallDagerMedRefusjon",
            "perioder[1].månedsinntekt",
            "perioder[2].antallDagerMedRefusjon"
        )
        val res = response.call.body<ValidationProblem>()
        assertThat(res.violations.size).isEqualTo(7)
        res.violations.forEach {
            assertThat(it.propertyPath).isIn(possiblePropertyPaths)
        }
    }

    @Test
    internal fun `Oppdaterer ikke når ny request er duplikat`() = suspendableTest {
        val repo by inject<KroniskKravRepository>()

        val krav = repo.insert(KroniskTestData.kroniskKravRequestValid.toDomain("hohoho", "god", "jul"))

        val response = httpClient.patch {
            appUrl("$kravKroniskUrl/${krav.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(KroniskTestData.kroniskKrav.identitetsnummer)
            setBody(KroniskTestData.kroniskKravRequestValid)
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
    }
}
