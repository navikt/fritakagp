package no.nav.helse.slowtests

import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.db.PostgresGravidKravRepository
import no.nav.helse.slowtests.utils.WithPostgresContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresGravidKravRepositoryTest : WithPostgresContainer() {
    val repo =
        PostgresGravidKravRepository(
            ds = dataSource,
            om = customObjectMapper(),
        )
    val testKrav = GravidTestData.gravidKrav

    @BeforeEach
    fun setUp() {
        repo.insert(testKrav)
    }

    @AfterEach
    fun tearDown() {
        repo.delete(testKrav.id)
    }

    @Test
    fun `test getById`() {
        val soeknadKroniskResult = repo.getById(testKrav.id)
        assertThat(soeknadKroniskResult).isEqualToIgnoringGivenFields(testKrav, "referansenummer")
        assertThat(soeknadKroniskResult!!.referansenummer).isNotNull
    }

    @Test
    fun `kan oppdatere data`() {
        val soeknadKroniskResult = repo.getById(testKrav.id)
        assertNotNull(soeknadKroniskResult, "Må finnes")

        soeknadKroniskResult.journalpostId = "1234"
        soeknadKroniskResult.oppgaveId = "78990"

        repo.update(soeknadKroniskResult)

        val afterUpdate = repo.getById(soeknadKroniskResult.id)
        assertThat(afterUpdate).isEqualTo(soeknadKroniskResult)
    }
}
