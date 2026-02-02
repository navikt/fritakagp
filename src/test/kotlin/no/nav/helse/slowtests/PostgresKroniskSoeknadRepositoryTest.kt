package no.nav.helse.slowtests

import com.zaxxer.hikari.HikariDataSource
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.db.PostgresKroniskSoeknadRepository
import no.nav.helse.fritakagp.db.createTestHikariConfig
import no.nav.helse.slowtests.systemtests.api.SystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get
import kotlin.test.assertNotNull

class PostgresKroniskSoeknadRepositoryTest : SystemTestBase() {

    lateinit var repo: PostgresKroniskSoeknadRepository
    val testSoeknad = KroniskTestData.soeknadKronisk

    @BeforeEach
    fun setUp() {
        val ds = HikariDataSource(createTestHikariConfig())

        repo = PostgresKroniskSoeknadRepository(ds, get())
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
