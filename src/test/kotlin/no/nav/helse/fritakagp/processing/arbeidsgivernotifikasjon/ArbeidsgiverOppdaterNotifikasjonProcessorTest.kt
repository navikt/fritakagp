package no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.helse.GravidTestData
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArbeidsgiverOppdaterNotifikasjonProcessorTest {
    val arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    val gravidKravRepositoryMock = mockk<GravidKravRepository>(relaxed = true)
    val kroniskKravRepositoryMock = mockk<KroniskKravRepository>(relaxed = true)
    val objectMapper = ObjectMapper().registerModule(
        KotlinModule.Builder()
            .withReflectionCacheSize(512)
            .configure(KotlinFeature.NullToEmptyCollection, false)
            .configure(KotlinFeature.NullToEmptyMap, false)
            .configure(KotlinFeature.NullIsSameAsDefault, false)
            .configure(KotlinFeature.SingletonSupport, false)
            .configure(KotlinFeature.StrictNullChecks, false)
            .build()
    ).registerModule(JavaTimeModule())

    val gravidKrav = GravidTestData.gravidKrav
    val kroniskKrav = KroniskTestData.kroniskKrav

    private var gravidJobb = Bakgrunnsjobb(data = "", type = "arbeidsgiveroppdaternotifikasjon")
    private var kroniskJobb = Bakgrunnsjobb(data = "", type = "arbeidsgiveroppdaternotifikasjon")

    val prosessor = ArbeidsgiverOppdaterNotifikasjonProcessor(
        gravidKravRepositoryMock,
        kroniskKravRepositoryMock,
        objectMapper,
        arbeidsgiverNotifikasjonKlient
    )

    @BeforeEach
    fun setup() {
        gravidJobb = Bakgrunnsjobb(data = objectMapper.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(gravidKrav.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.GravidKrav)), type = "arbeidsgiveroppdaternotifikasjon")
        kroniskJobb = Bakgrunnsjobb(data = objectMapper.writeValueAsString(ArbeidsgiverNotifikasjonProcessor.JobbData(kroniskKrav.id, ArbeidsgiverNotifikasjonProcessor.JobbData.SkjemaType.KroniskKrav)), type = "arbeidsgiveroppdaternotifikasjon")
        every { gravidKravRepositoryMock.getById(gravidKrav.id) } returns gravidKrav
        every { kroniskKravRepositoryMock.getById(kroniskKrav.id) } returns kroniskKrav
    }

    @Test
    fun `Oppdaterer sak mot arbeidsgiver-notifikasjoner for gravidKrav`() {
        prosessor.prosesser(gravidJobb)
        coVerify(exactly = 1) { arbeidsgiverNotifikasjonKlient.nyStatusSakByGrupperingsid(gravidKrav.id.toString(), any(), SaksStatus.MOTTATT, any(), any(), "2023-12-24T10:00:00+01:00") }
    }

    @Test
    fun `Oppdaterer sak mot arbeidsgiver-notifikasjoner for kroniskKrav`() {
        prosessor.prosesser(kroniskJobb)
        coVerify(exactly = 1) { arbeidsgiverNotifikasjonKlient.nyStatusSakByGrupperingsid(kroniskKrav.id.toString(), any(), SaksStatus.MOTTATT, any(), any(), "2023-12-24T10:00:00+01:00") }
    }
}
