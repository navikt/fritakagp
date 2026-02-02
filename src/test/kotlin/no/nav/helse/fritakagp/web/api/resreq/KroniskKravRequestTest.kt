package no.nav.helse.fritakagp.web.api.resreq

import no.nav.helse.AaregTestData
import no.nav.helse.KroniskTestData
import no.nav.helsearbeidsgiver.utils.test.date.januar
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class KroniskKravRequestTest {
    val navn = "Personliga Person"
    val sendtAv = "123"
    val sendtAvNavn = "Ola M Avsender"

    @Test
    fun `Antall dager kan ikke være mer enn dager i året`() {
        validationShouldFailFor(KroniskKravRequest::antallDager) {
            KroniskTestData.kroniskKravRequestValid.copy(antallDager = 367).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Antall dager kan ikke være negativt`() {
        validationShouldFailFor(KroniskKravRequest::antallDager) {
            KroniskTestData.kroniskKravRequestValid.copy(antallDager = -1).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Antall dager må være 1-366`() {
        validationShouldFailFor(KroniskKravRequest::antallDager) {
            KroniskTestData.kroniskKravRequestValid.copy(antallDager = 0).validate(AaregTestData.evigAnsettelsesperiode)
        }
        validationShouldFailFor(KroniskKravRequest::antallDager) {
            KroniskTestData.kroniskKravRequestValid.copy(antallDager = 367).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Gyldig FNR er påkrevd`() {
        validationShouldFailFor(KroniskKravRequest::identitetsnummer) {
            KroniskTestData.kroniskKravRequestValid.copy(identitetsnummer = "01020312345").validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Gyldig OrgNr er påkrevd dersom det er oppgitt`() {
        validationShouldFailFor(KroniskKravRequest::virksomhetsnummer) {
            KroniskTestData.kroniskKravRequestValid.copy(virksomhetsnummer = "098765432").validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Bekreftelse av egenerklæring er påkrevd`() {
        validationShouldFailFor(KroniskKravRequest::bekreftet) {
            KroniskTestData.kroniskKravRequestValid.copy(bekreftet = false).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `mapping til domenemodell setter harVedlegg til default false - dokumentasjon er fjernet fra krav`() {
        Assertions.assertThat(KroniskTestData.kroniskKravRequestValid.tilKrav(sendtAv, sendtAvNavn, navn, emptyList()).harVedlegg).isFalse
    }

    @Test
    fun `Antall refusjonsdager kan ikke overstige periodelengden`() {
        validationShouldFailFor("perioder[0].antallDagerMedRefusjon") {
            KroniskTestData.kroniskKravRequestValid.copy(
                perioder = listOf(KroniskTestData.kroniskKravRequestValid.perioder.first().copy(antallDagerMedRefusjon = 21))
            ).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Til dato kan ikke komme før fra dato`() {
        validationShouldFailFor("perioder[0].fom") {
            KroniskTestData.kroniskKravRequestValid.copy(
                perioder = listOf(
                    KroniskTestData.kroniskKravRequestValid.perioder.first().copy(
                        fom = 10.januar(2020),
                        tom = 5.januar(2020),
                        antallDagerMedRefusjon = -5
                    )
                ) // slik at validationShouldFailFor() kaster ikke to unntak
            ).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }

    @Test
    fun `Sykemeldingsgrad må være gyldig`() {
        validationShouldFailFor("perioder[0].gradering") {
            KroniskTestData.kroniskKravRequestValid.copy(
                perioder = listOf(KroniskTestData.kroniskKravRequestValid.perioder.first().copy(gradering = 1.1))
            ).validate(AaregTestData.evigAnsettelsesperiode)
        }

        validationShouldFailFor("perioder[0].gradering") {
            KroniskTestData.kroniskKravRequestValid.copy(
                perioder = listOf(KroniskTestData.kroniskKravRequestValid.perioder.first().copy(gradering = 0.1))
            ).validate(AaregTestData.evigAnsettelsesperiode)
        }
    }
}
