package no.nav.helse.fritakagp.processing.kronisk.soeknad

import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.domain.FravaerData
import no.nav.helse.fritakagp.domain.generereKroniskSoeknadBeskrivelse
import no.nav.helse.toYearMonthString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertTrue

class KroniskSoeknadAntallDagerFloatTest {

    @Test
    fun `Skal summere antall dager korrekt`() {
        val testData = setOf(
            FravaerData(LocalDate.now().minusMonths(4).toYearMonthString(), 5F),
            FravaerData(LocalDate.now().minusMonths(3).toYearMonthString(), 5.3F)
        )

        assertEquals(10.3F, testData.map { it.antallDagerMedFravaer }.sum())
    }

    @Test
    fun `Beskrivelse skal inneholde riktig genererte string `() {
        val testData = setOf(
            FravaerData(LocalDate.now().minusMonths(4).toYearMonthString(), 5F),
            FravaerData(LocalDate.now().minusMonths(3).toYearMonthString(), 5.3F)
        )
        val result = generereKroniskSoeknadBeskrivelse(KroniskTestData.soeknadKronisk.copy(fravaer = testData), "Test")

        assertTrue(result.contains("10.3"))
        println(result)
    }
}
