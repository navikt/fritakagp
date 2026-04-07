package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.web.api.resreq.ArbeidsgiverperiodeRequest
import no.nav.helse.fritakagp.web.api.resreq.validation.ValidationProblem
import no.nav.helsearbeidsgiver.utils.test.date.januar
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.koin.test.inject

class GravidKravHTTPTests : SystemTestBase() {
    private val kravGravidUrl = "/fritak-agp-api/api/v1/gravid/krav"

    @Test
    @Disabled
    fun `Returnerer kravet når korrekt bruker er innlogget, 403 når ikke`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        repo.insert(GravidTestData.gravidKrav)
        val response =
            httpClient.get {
                appUrl("$kravGravidUrl/${GravidTestData.gravidKrav.id}")
                contentType(ContentType.Application.Json)
                loggedInAs("123456789")
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)

        val accessGrantedForm = httpClient.get {
            appUrl("$kravGravidUrl/${GravidTestData.gravidKrav.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(GravidTestData.gravidKrav.identitetsnummer)
        }

        assertThat(accessGrantedForm).isEqualTo(GravidTestData.gravidKrav)
    }

    @Test
    fun `Gir not found når kravet er slettet`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        repo.insert(GravidTestData.gravidKrav.copy(status = KravStatus.SLETTET))

        val response = httpClient.get {
            appUrl("$kravGravidUrl/${GravidTestData.gravidKrav.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(GravidTestData.gravidKrav.identitetsnummer)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `Gir ikke not found når kravet er slettet og flagget slettet er satt`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        repo.insert(GravidTestData.gravidKrav.copy(status = KravStatus.SLETTET))

        val response = httpClient.get {
            appUrl("$kravGravidUrl/${GravidTestData.gravidKrav.id}?slettet")
            contentType(ContentType.Application.Json)
            loggedInAs(GravidTestData.gravidKrav.identitetsnummer)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `invalid json gives 400 Bad request`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(kravGravidUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(GravidTestData.validIdentitetsnummer)

                setBody(
                    """
                    {
                        "fnr": "${GravidTestData.validIdentitetsnummer}",
                        "orgnr": "${GravidTestData.fullValidSoeknadRequest.virksomhetsnummer}",
                        "tilrettelegge": true,
                        "tiltak": ["IKKE GYLDIG"]
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
            appUrl(kravGravidUrl)
            contentType(ContentType.Application.Json)
            loggedInAs(GravidTestData.validIdentitetsnummer)
            setBody(GravidTestData.gravidKravRequestValid)
        }.body<GravidKrav>()
        assertThat(response.status).isEqualTo(KravStatus.OPPRETTET)
        assertThat(response.identitetsnummer).isEqualTo(GravidTestData.gravidKravRequestValid.identitetsnummer)
    }

    @Test
    fun `Skal returnere Created ved periode på en dag`() = suspendableTest {
        val response = httpClient.post {
            appUrl(kravGravidUrl)
            contentType(ContentType.Application.Json)
            loggedInAs(GravidTestData.validIdentitetsnummer)
            setBody(GravidTestData.gravidKravRequestValidPeriode1Dag)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val krav = response.body<GravidKrav>()
        assertThat(krav.identitetsnummer).isEqualTo(GravidTestData.gravidKravRequestValidPeriode1Dag.identitetsnummer)
    }

    @Test
    fun `Skal returnere forbidden hvis virksomheten ikke er i auth listen fra altinn`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(kravGravidUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(GravidTestData.validIdentitetsnummer)
                setBody(GravidTestData.gravidKravRequestValid.copy(virksomhetsnummer = "123456785"))
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    fun `Skal returnere en valideringfeil`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(kravGravidUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(GravidTestData.validIdentitetsnummer)
                setBody(
                    GravidTestData.gravidKravRequestInValid.copy(
                        perioder = listOf(
                            ArbeidsgiverperiodeRequest(
                                fom = 15.januar(2020),
                                tom = 10.januar(2020),
                                antallDagerMedRefusjon = 2,
                                månedsinntekt = 2590.8
                            ),
                            ArbeidsgiverperiodeRequest(
                                fom = 5.januar(2020),
                                tom = 4.januar(2020),
                                antallDagerMedRefusjon = 2,
                                månedsinntekt = 2590.8
                            ),
                            ArbeidsgiverperiodeRequest(
                                fom = 5.januar(2020),
                                tom = 14.januar(2020),
                                antallDagerMedRefusjon = 12,
                                månedsinntekt = 2590.8
                            )
                        )
                    )
                )
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.UnprocessableEntity)
        val res = response.call.body<ValidationProblem>()
        assertThat(res.violations.size).isEqualTo(5)
    }

    @Test
    fun `Skal returnere full propertypath for periode`() = suspendableTest {
        val response =
            httpClient.post {
                appUrl(kravGravidUrl)
                contentType(ContentType.Application.Json)
                loggedInAs(GravidTestData.validIdentitetsnummer)
                setBody(
                    GravidTestData.gravidKravRequestInValid.copy(
                        perioder = listOf(
                            ArbeidsgiverperiodeRequest(
                                fom = 15.januar(2020),
                                tom = 10.januar(2020),
                                antallDagerMedRefusjon = 2,
                                månedsinntekt = 2590.8
                            ),
                            ArbeidsgiverperiodeRequest(
                                fom = 5.januar(2020),
                                tom = 4.januar(2020),
                                antallDagerMedRefusjon = 2,
                                månedsinntekt = 2590.8
                            ),
                            ArbeidsgiverperiodeRequest(
                                fom = 5.januar(2020),
                                tom = 14.januar(2020),
                                antallDagerMedRefusjon = 12,
                                månedsinntekt = 2590.8
                            )
                        )
                    )
                )
            }

        val possiblePropertyPaths = setOf(
            "perioder[0].fom",
            "perioder[0].antallDagerMedRefusjon",
            "perioder[1].fom",
            "perioder[1].antallDagerMedRefusjon",
            "perioder[2].antallDagerMedRefusjon"
        )
        val res = response.call.body<ValidationProblem>()
        assertThat(res.violations.size).isEqualTo(5)
        res.violations.forEach {
            assertThat(it.propertyPath).isIn(possiblePropertyPaths)
        }
    }
}
