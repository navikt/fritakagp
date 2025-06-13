package no.nav.helse.fritakagp.web.api.resreq

import io.mockk.every
import io.mockk.mockk
import no.nav.helse.AaregTestData
import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.domain.BeloepBeregning
import no.nav.helse.fritakagp.integration.GrunnbeloepClient
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GravidKravRequestTest {
    val navn = "Personliga Person"
    val sendtAv = "123"
    val sendtAvNavn = "Ola M Avsender"

    @Test
    internal fun `Antall dager kan ikke være mer enn dager i året`() {
        validationShouldFailFor(GravidKravRequest::antallDager) {
            GravidTestData.gravidKravRequestValid.copy(antallDager = 367).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    internal fun `Antall dager kan ikke være negativt`() {
        validationShouldFailFor(GravidKravRequest::antallDager) {
            GravidTestData.gravidKravRequestValid.copy(antallDager = -1).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    internal fun `Antall dager må være 1-366`() {
        validationShouldFailFor(GravidKravRequest::antallDager) {
            GravidTestData.gravidKravRequestValid.copy(antallDager = 0).validate(AaregTestData.evigAnsettelsesperiode)
        }
        validationShouldFailFor(GravidKravRequest::antallDager) {
            GravidTestData.gravidKravRequestValid.copy(antallDager = 367).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    internal fun `Gyldig FNR er påkrevd`() {
        validationShouldFailFor(GravidKravRequest::identitetsnummer) {
            GravidTestData.gravidKravRequestValid.copy(identitetsnummer = "01020312345").validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    internal fun `Gyldig OrgNr er påkrevd dersom det er oppgitt`() {
        validationShouldFailFor(GravidKravRequest::virksomhetsnummer) {
            GravidTestData.gravidKravRequestValid.copy(virksomhetsnummer = "098765432").validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    internal fun `Sykemeldingsgrad må være gyldig`() {
        validationShouldFailFor("perioder[0].gradering") {
            GravidTestData.gravidKravRequestValid.copy(
                perioder = listOf(GravidTestData.gravidKravRequestValid.perioder.first().copy(gradering = 1.1))
            ).validate(AaregTestData.evigAnsettelsesperiode)
        }

        validationShouldFailFor("perioder[0].gradering") {
            GravidTestData.gravidKravRequestValid.copy(
                perioder = listOf(GravidTestData.gravidKravRequestValid.perioder.first().copy(gradering = 0.1))
            ).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    internal fun `Bekreftelse av egenerklæring er påkrevd`() {
        validationShouldFailFor(GravidKravRequest::bekreftet) {
            GravidTestData.gravidKravRequestValid.copy(bekreftet = false).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    internal fun `mapping til domenemodell setter harVedlegg til false - støtte for vedlegg er fjernet fra krav`() {
        assertThat(GravidTestData.gravidKravRequestValid.toDomain(sendtAv, sendtAvNavn, navn).harVedlegg).isFalse
    }

    @Test
    internal fun `Antall refusjonsdager kan ikke overstige periodelengden`() {
        validationShouldFailFor("perioder[0].antallDagerMedRefusjon") {
            GravidTestData.gravidKravRequestValid.copy(
                perioder = listOf(GravidTestData.gravidKravRequestValid.perioder.first().copy(antallDagerMedRefusjon = 21))
            ).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    internal fun `Til dato kan ikke komme før fra dato`() {
        validationShouldFailFor("perioder[0].fom") {
            GravidTestData.gravidKravRequestValid.copy(
                perioder = listOf(
                    GravidTestData.gravidKravRequestValid.perioder.first().copy(
                        fom = LocalDate.of(2020, 1, 10),
                        tom = LocalDate.of(2020, 1, 5),
                        antallDagerMedRefusjon = -5
                    )
                ) // slik at validationShouldFailFor() kaster ikke to unntak
            ).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Beløp og dagsats er beregnet`() {
        val grunnbeloepClient = mockk<GrunnbeloepClient>(relaxed = true)
        every { grunnbeloepClient.hentGrunnbeløp(any()).grunnbeløp } returns 106399

        val belopBeregning = BeloepBeregning(grunnbeloepClient)
        val krav = GravidTestData.gravidKravRequestValid.toDomain(sendtAv, sendtAvNavn, navn)
        belopBeregning.beregnBeløpGravid(krav)

        assertThat(krav.perioder.first().dagsats).isEqualTo(7772.4)
        assertThat(krav.perioder.first().belop).isEqualTo(12435.84)
    }

    @Test
    fun `Beløp har riktig desimaltall`() {
        val grunnbeloepClient = mockk<GrunnbeloepClient>(relaxed = true)
        every { grunnbeloepClient.hentGrunnbeløp(any()).grunnbeløp } returns 106399

        val belopBeregning = BeloepBeregning(grunnbeloepClient)
        val krav = GravidTestData.gravidKravRequestWithWrongDecimal.toDomain(sendtAv, sendtAvNavn, navn)
        belopBeregning.beregnBeløpGravid(krav)

        assertThat(krav.perioder.first().belop).isEqualTo(2848.6)
    }
}
