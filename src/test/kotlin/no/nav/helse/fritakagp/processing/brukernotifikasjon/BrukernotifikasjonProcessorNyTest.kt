package no.nav.helse.fritakagp.processing.brukernotifikasjon

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.helse.GravidTestData
import no.nav.helse.KroniskTestData
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonSender
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Oppretting
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.SkjemaType
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.SkjemaType.KroniskKrav
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.SkjemaType.KroniskSøknad
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.builder.BuilderEnvironment
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class BrukernotifikasjonProcessorNyTest {

    val ksRepo = mockk<KroniskSoeknadRepository>(relaxed = true)
    val kkRepo = mockk<KroniskKravRepository>(relaxed = true)
    val gkRepo = mockk<GravidKravRepository>(relaxed = true)
    val gsRepo = mockk<GravidSoeknadRepository>(relaxed = true)
    val kafkaSenderMock = mockk<BrukernotifikasjonSender>(relaxed = true)

    val objectMapper = customObjectMapper()

    val service = BrukernotifikasjonService(objectMapper, Sensitivitet.High)
    val prosessor = BrukernotifikasjonProcessorNy(kafkaSenderMock, service)

    private var jobb = BakgrunnsJobbUtils.emptyJob()

    @BeforeEach
    fun setup() {
        mapOf(
            "NAIS_APP_NAME" to "test-app",
            "NAIS_NAMESPACE" to "test-namespace",
            "NAIS_CLUSTER_NAME" to "dev"
        ).let { naisEnv ->
            BuilderEnvironment.extend(naisEnv)
        }

        every { ksRepo.getById(any()) } returns KroniskTestData.soeknadKronisk
        every { kkRepo.getById(any()) } returns KroniskTestData.kroniskKrav
        every { gkRepo.getById(any()) } returns GravidTestData.gravidKrav
        every { gsRepo.getById(any()) } returns GravidTestData.soeknadGravid
    }

    @Test
    fun `skal sende kafkamelding med brukernotifikasjon for Kronisk Krav`() {
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(
                BrukernotifikasjonJobbdata(UUID.randomUUID(), KroniskTestData.validIdentitetsnummer, KroniskTestData.validOrgNr, KroniskKrav, Oppretting)
            )
        )
        prosessor.prosesser(jobb)

        verify(exactly = 1) { kafkaSenderMock.sendMessage(any(), any()) }
    }

    @Test
    fun `skal sende kafkamelding med brukernotifikasjon for Kronisk Søknad`() {
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(
                BrukernotifikasjonJobbdata(UUID.randomUUID(), KroniskTestData.validIdentitetsnummer, KroniskTestData.validOrgNr, KroniskSøknad, Oppretting)
            )
        )
        prosessor.prosesser(jobb)

        verify(exactly = 1) { kafkaSenderMock.sendMessage(any(), any()) }
    }

    @Test
    fun `skal sende kafkamelding med brukernotifikasjon for Gravid Krav`() {
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(
                BrukernotifikasjonJobbdata(UUID.randomUUID(), GravidTestData.validIdentitetsnummer, GravidTestData.validOrgNr, SkjemaType.GravidKrav, Oppretting)
            )
        )
        prosessor.prosesser(jobb)

        verify(exactly = 1) { kafkaSenderMock.sendMessage(any(), any()) }
    }

    @Test
    fun `skal sende kafkamelding med brukernotifikasjon for Gravid Søknad`() {
        jobb = BakgrunnsJobbUtils.testJob(
            objectMapper.writeValueAsString(
                BrukernotifikasjonJobbdata(UUID.randomUUID(), GravidTestData.validIdentitetsnummer, GravidTestData.validOrgNr, SkjemaType.GravidSøknad, Oppretting)
            )
        )
        prosessor.prosesser(jobb)

        verify(exactly = 1) { kafkaSenderMock.sendMessage(any(), any()) }
    }
}
