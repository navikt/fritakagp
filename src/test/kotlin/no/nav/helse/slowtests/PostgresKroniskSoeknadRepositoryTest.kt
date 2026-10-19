package no.nav.helse.slowtests

import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.db.PostgresKroniskSoeknadRepository
import no.nav.helse.slowtests.utils.WithPostgresContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresKroniskSoeknadRepositoryTest : WithPostgresContainer() {
    val repo =
        PostgresKroniskSoeknadRepository(
            ds = dataSource,
            om = customObjectMapper(),
        )
    val testSoeknad = KroniskTestData.soeknadKronisk

    @BeforeEach
    fun setUp() {
        repo.insert(testSoeknad)
    }

    @AfterEach
    fun tearDown() {
        repo.delete(testSoeknad.id)
    }

    @Test
    fun finnerDataIDb() {
        val soeknadKroniskResult = repo.getById(testSoeknad.id)
        assertThat(soeknadKroniskResult).isEqualToIgnoringGivenFields(testSoeknad, "referansenummer")
        assertThat(soeknadKroniskResult!!.referansenummer).isNotNull
    }

    @Test
    fun kanOppdatereData() {
        val soeknadKroniskResult = repo.getById(testSoeknad.id)
        assertNotNull(soeknadKroniskResult, "Må finnes")

        soeknadKroniskResult.journalpostId = "1234"
        soeknadKroniskResult.oppgaveId = "78990"

        repo.update(soeknadKroniskResult)

        val afterUpdate = repo.getById(soeknadKroniskResult.id)
        assertThat(afterUpdate).isEqualTo(soeknadKroniskResult)
    }
}
