package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.call.body
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.mockArbeidsgiverperiode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.koin.test.inject
import java.util.UUID

class OppdaterGravidKravHTTPTests : SystemTestBase() {
    private val kravGravidUrl = "/fritak-agp-api/api/v1/gravid/krav"

    @Test
    fun `Returnerer endret krav når korrekt bruker er innlogget`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        repo.insert(GravidTestData.gravidKrav)

        val accessGrantedForm = httpClient.patch {
            appUrl("$kravGravidUrl/${GravidTestData.gravidKrav.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(GravidTestData.gravidKrav.identitetsnummer)
            setBody(GravidTestData.gravidKravRequestValid)
        }.body<GravidKrav>()

        //  <GravidKrav(id=9e1495fb-ed15-4104-81bb-ad29c53d0beb, opprettet=2022-03-06T13:17:51.347218, sendtAv=20015001543, virksomhetsnummer=917404437, identitetsnummer=20015001543, navn=Ola M Avsender, perioder=[Arbeidsgiverperiode(fom=2020-01-05, tom=2020-01-10, antallDagerMedRefusjon=2, månedsinntekt=2590.8, gradering=0.8)], harVedlegg=false, kontrollDager=null, antallDager=4, journalpostId=null, oppgaveId=null, virksomhetsnavn=null, sendtAvNavn=Ola M Avsender, sletteJournalpostId=null, sletteOppgaveId=null, slettetAv=null, slettetAvNavn=null, status=OPPRETTET, aarsakEndring=null, endretDato=null)>
        assertThat(accessGrantedForm.virksomhetsnummer).isEqualTo(GravidTestData.gravidKravRequestValid.virksomhetsnummer)
        assertThat(accessGrantedForm.perioder).isEqualTo(GravidTestData.gravidKravRequestValid.perioder)

        val slettetKrav = repo.getById(GravidTestData.gravidKrav.id)
        assertThat(slettetKrav?.status).isEqualTo(KravStatus.ENDRET)
    }

    @Test
    fun `Oppdaterer ikke når ny request er duplikat`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        val krav = repo.insert(GravidTestData.gravidKravRequestValid.tilKrav("hohoho", "god", "jul", listOf(mockArbeidsgiverperiode())))

        val response = httpClient.patch {
            appUrl("$kravGravidUrl/${krav.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(GravidTestData.gravidKrav.identitetsnummer)
            setBody(GravidTestData.gravidKravRequestValid)
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Conflict)
    }

    @Test
    fun `Skal returnere 404 når kravet ikke finnes`() = suspendableTest {
        val response =
            httpClient.patch {
                appUrl("$kravGravidUrl/${UUID.randomUUID()}")
                contentType(ContentType.Application.Json)
                loggedInAs(GravidTestData.gravidKrav.identitetsnummer)
                setBody(GravidTestData.gravidKravRequestValid)
            }

        assertThat(response.status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun `Skal returnere forbidden hvis virksomheten ikke er i auth listen fra altinn`() = suspendableTest {
        val repo by inject<GravidKravRepository>()

        repo.insert(GravidTestData.gravidKrav)

        val response =
            httpClient.patch {
                appUrl("$kravGravidUrl/${GravidTestData.gravidKrav.id}")
                contentType(ContentType.Application.Json)
                loggedInAs(GravidTestData.validIdentitetsnummer)
                setBody(GravidTestData.gravidKravRequestValid.copy(virksomhetsnummer = "123456785"))
            }
        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
    }
}
