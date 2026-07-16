package no.nav.helse.slowtests.systemtests.api

import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import no.nav.helse.GravidTestData
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helsearbeidsgiver.utils.test.wrapper.genererGyldig
import no.nav.helsearbeidsgiver.utils.wrapper.Fnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.koin.test.inject
import java.util.UUID

class SoeknadOrgTilgangHTTPTests : SystemTestBase() {

    @Test
    fun `gravid soeknad - kaller validerTilgangTilOrganisasjon og returnerer 403 ved manglende tilgang`() = suspendableTest {
        val repo by inject<GravidSoeknadRepository>()
        val soeknad = GravidTestData.soeknadGravid.copy(id = UUID.randomUUID(), virksomhetsnummer = "999999999")
        repo.insert(soeknad)

        val response = httpClient.get {
            appUrl("/fritak-agp-api/api/v1/gravid/soeknad/${soeknad.id}")
            contentType(ContentType.Application.Json)
            val fnr = Fnr.genererGyldig()
            loggedInAs(fnr.verdi)
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    fun `gravid soeknad - bruker får tilgang til skjema på vegne av seg selv`() = suspendableTest {
        val repo by inject<GravidSoeknadRepository>()
        val fnr = Fnr.genererGyldig()

        val soeknad = GravidTestData.soeknadGravid.copy(identitetsnummer = fnr.verdi, id = UUID.randomUUID(), virksomhetsnummer = "999999999")
        repo.insert(soeknad)

        val response = httpClient.get {
            appUrl("/fritak-agp-api/api/v1/gravid/soeknad/${soeknad.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(fnr.verdi)
        }
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `kronisk soeknad - kaller validerTilgangTilOrganisasjon og returnerer 403 ved manglende tilgang`() = suspendableTest {
        val repo by inject<KroniskSoeknadRepository>()
        val soeknad = KroniskTestData.soeknadKronisk.copy(id = UUID.randomUUID(), virksomhetsnummer = "999999999")
        repo.insert(soeknad)
        val response = httpClient.get {
            appUrl("/fritak-agp-api/api/v1/kronisk/soeknad/${soeknad.id}")
            contentType(ContentType.Application.Json)
            val fnr = Fnr.genererGyldig()
            loggedInAs(fnr.verdi)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    fun `kronisk soeknad - bruker får tilgang til skjema på vegne av seg selv`() = suspendableTest {
        val repo by inject<KroniskSoeknadRepository>()
        val fnr = Fnr.genererGyldig()
        val soeknad = KroniskTestData.soeknadKronisk.copy(identitetsnummer = fnr.verdi, id = UUID.randomUUID(), virksomhetsnummer = "999999999")
        repo.insert(soeknad)
        val response = httpClient.get {
            appUrl("/fritak-agp-api/api/v1/kronisk/soeknad/${soeknad.id}")
            contentType(ContentType.Application.Json)
            loggedInAs(fnr.verdi)
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }
}
