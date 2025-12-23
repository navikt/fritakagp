package no.nav.helse.slowtests

import com.zaxxer.hikari.HikariDataSource
import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.db.PostgresKroniskKravRepository
import no.nav.helse.fritakagp.db.createTestHikariConfig
import no.nav.helse.slowtests.systemtests.api.SystemTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.core.component.get

class PostgresKroniskKravRepositoryTest : SystemTestBase() {

    lateinit var repo: PostgresKroniskKravRepository
    val testKrav = KroniskTestData.kroniskKrav

    @BeforeEach
    internal fun setUp() {
        val ds = HikariDataSource(createTestHikariConfig())

        repo = PostgresKroniskKravRepository(ds, get())
        repo.insert(testKrav)
    }

    @AfterEach
    internal fun tearDown() {
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
        val soeknadKroniskResult = repo.getById(testKrav.id).shouldNotBeNull()

        soeknadKroniskResult.journalpostId = "1234"
        soeknadKroniskResult.oppgaveId = "78990"

        repo.update(soeknadKroniskResult)

        val afterUpdate = repo.getById(soeknadKroniskResult.id)
        assertThat(afterUpdate).isEqualTo(soeknadKroniskResult)
    }
}
