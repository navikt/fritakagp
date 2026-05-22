package no.nav.helse.fritakagp.domain

import no.nav.helse.KroniskTestData
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KroniskSoeknadFravaerPerAarTest {

    @Test
    fun `fravaerPerAar skal gruppere fravaer per år`() {
        val fravaer = setOf(
            FravaerData("2023-01", 3F),
            FravaerData("2023-06", 4F),
            FravaerData("2024-03", 5F),
            FravaerData("2024-09", 2.5F),
            FravaerData("2025-02", 6F)
        )

        val soeknad = KroniskTestData.soeknadKronisk.copy(fravaer = fravaer)
        val result = soeknad.fravaerPerAar

        assertEquals(3, result.size)
        assertEquals(7F, result.first { it.aar == "2023" }.antallDager)
        assertEquals(7.5F, result.first { it.aar == "2024" }.antallDager)
        assertEquals(6F, result.first { it.aar == "2025" }.antallDager)
    }

    @Test
    fun `fravaerPerAar med tomt set skal returnere tomt set`() {
        val soeknad = KroniskTestData.soeknadKronisk.copy(fravaer = emptySet())
        val result = soeknad.fravaerPerAar

        assertTrue(result.isEmpty())
    }

    @Test
    fun `fravaerPerAar med null dager skal returnere 0`() {
        val fravaer = setOf(
            FravaerData("2023-03", 0F),
            FravaerData("2023-07", 0F)
        )

        val soeknad = KroniskTestData.soeknadKronisk.copy(fravaer = fravaer)
        val result = soeknad.fravaerPerAar

        assertEquals(setOf(FravaerPerAar("2023", 0F)), result)
    }
}
