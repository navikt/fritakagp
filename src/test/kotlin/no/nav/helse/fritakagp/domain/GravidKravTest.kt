package no.nav.helse.fritakagp.domain

import no.nav.helse.GravidTestData
import no.nav.helsearbeidsgiver.utils.test.date.januar
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GravidKravTest {

    @Test
    fun `Duplikatsjekk - orginal skal være lik seg selv`() {
        val krav = GravidTestData.gravidKrav
        assertTrue(krav.isDuplicate(krav))
    }

    @Test
    fun `Duplikatsjekk - forskjellige verdier i virksomhetsnummer gir ingen duplikat-treff`() {
        val orginalKrav = GravidTestData.gravidKrav
        val nyttKrav = orginalKrav.copy(virksomhetsnummer = "noe helt forskjellig")
        assertFalse(orginalKrav.isDuplicate(nyttKrav))
    }

    @Test
    fun `Duplikatsjekk - forskjellige verdier i identititetsnummer gir ingen duplikat-treff`() {
        val orginalKrav = GravidTestData.gravidKrav
        val nyttKrav = orginalKrav.copy(identitetsnummer = "noe helt forskjellig")
        assertFalse(orginalKrav.isDuplicate(nyttKrav))
    }

    @Test
    fun `Duplikatsjekk - forskjellige arbeidsgiverperioder gir ingen duplikat-treff`() {
        val agp1 = Arbeidsgiverperiode(
            fom = 1.januar(2020),
            tom = 16.januar(2020),
            antallDagerMedRefusjon = 10,
            månedsinntekt = 1000.0,
            gradering = 1.0,
            dagsats = 111.0,
            belop = 1110.0
        )
        val endretFraDato = agp1.copy(fom = agp1.fom.plusDays(1))
        val endretTilDato = agp1.copy(tom = agp1.tom.plusDays(1))
        val endretRefusjon = agp1.copy(antallDagerMedRefusjon = 11)
        val endretInntekt = agp1.copy(månedsinntekt = 1001.0)
        val endretGradering = agp1.copy(gradering = 0.8)
        val perioder = listOf(agp1, endretFraDato, endretTilDato, endretRefusjon, endretInntekt, endretGradering)

        perioder.allPairs().forEach {
            val krav1 = GravidTestData.gravidKrav.copy(perioder = listOf(it.first))
            val krav2 = GravidTestData.gravidKrav.copy(perioder = listOf(it.second))
            assertFalse(krav1.isDuplicate(krav2))
        }
    }

    @Test
    fun `Duplikatsjekk - forskjellige verdier i kontrolldager gir ingen duplikat-treff`() {
        val orginalKrav = GravidTestData.gravidKrav.copy(kontrollDager = 14)
        val nyttKrav = orginalKrav.copy(kontrollDager = 3)
        assertFalse(orginalKrav.isDuplicate(nyttKrav))
    }

    @Test
    fun `Duplikatsjekk - forskjellige verdier i antallDager gir ingen duplikat-treff`() {
        val orginalKrav = GravidTestData.gravidKrav.copy(antallDager = 1)
        val nyttKrav = orginalKrav.copy(antallDager = 2)
        assertFalse(orginalKrav.isDuplicate(nyttKrav))
    }

    fun <T> List<T>.allPairs(): List<Pair<T, T>> {
        val pairs = mutableListOf<Pair<T, T>>()
        for (i in 0 until size) {
            for (j in i + 1 until size) {
                pairs.add(this[i] to this[j])
            }
        }
        return pairs
    }
}
