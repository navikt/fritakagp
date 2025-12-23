package no.nav.helse.fritakagp.processing.gravid.krav

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.integration.PdlService
import no.nav.helse.fritakagp.integration.arbeidsgiver.OppgaveKlient
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils.emptyJob
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils.testJob
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.UUID

class GravidKravEndreProcessorTest {

    val joarkMock = mockk<DokArkivClient>(relaxed = true)
    val oppgaveMock = mockk<OppgaveKlient>(relaxed = true)
    val repositoryMock = mockk<GravidKravRepository>(relaxed = true)
    val pdlServiceMock = mockk<PdlService>(relaxed = true)
    val objectMapper = customObjectMapper()
    val pdfGeneratorMock = mockk<GravidKravPDFGenerator>(relaxed = true)
    val bucketStorageMock = mockk<BucketStorage>(relaxed = true)
    val bakgrunnsjobbRepositoryMock = mockk<BakgrunnsjobbRepository>(relaxed = true)

    val prosessor = GravidKravEndreProcessor(repositoryMock, joarkMock, oppgaveMock, pdlServiceMock, pdfGeneratorMock, objectMapper, bucketStorageMock, bakgrunnsjobbRepositoryMock)
    lateinit var endretKrav: GravidKrav
    lateinit var oppdatertKrav: GravidKrav

    private val oppgaveId = 9999
    private val arkivReferanse = "12345"
    private var jobb = emptyJob()

    @BeforeEach
    fun setup() {
        val uuid = UUID.randomUUID()
        endretKrav = GravidTestData.gravidEndretKrav.copy(endretTilId = uuid)
        oppdatertKrav = GravidTestData.gravidKrav.copy(id = uuid)
        jobb = testJob(objectMapper.writeValueAsString(GravidKravProcessor.JobbData(endretKrav.id)))
        every { repositoryMock.getById(endretKrav.id) } returns endretKrav
        every { repositoryMock.getById(endretKrav.endretTilId!!) } returns oppdatertKrav
        every { bucketStorageMock.getDocAsString(any()) } returns null
        every { pdlServiceMock.hentAktoerId(endretKrav.identitetsnummer) } returns "aktør-id"
        coEvery { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns OpprettOgFerdigstillResponse(arkivReferanse, true, null, emptyList())
        coEvery { oppgaveMock.opprettOppgave(any(), any()) } returns GravidTestData.gravidOpprettOppgaveResponse.copy(id = oppgaveId)
    }

    @Test
    fun `skal journalføre, opprette oppgave og oppdatere kravet i databasen`() {
        prosessor.prosesser(jobb)

        assertThat(oppdatertKrav.journalpostId).isEqualTo(arkivReferanse)
        assertThat(oppdatertKrav.oppgaveId).isEqualTo(oppgaveId.toString())

        coVerify(exactly = 1) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            oppgaveMock.opprettOppgave(
                withArg {
                    assertEquals("BEH_REF", it.oppgavetype)
                },
                any()
            )
        }

        verify(exactly = 1) { repositoryMock.update(oppdatertKrav) }
    }

    @Test
    fun `Ved feil i oppgave skal joarkref lagres, og det skal det kastes exception oppover`() {
        coEvery { oppgaveMock.opprettOppgave(any(), any()) } throws IOException()

        assertThrows<IOException> { prosessor.prosesser(jobb) }

        assertThat(oppdatertKrav.journalpostId).isEqualTo(arkivReferanse)
        assertThat(oppdatertKrav.oppgaveId).isNull()

        coVerify(exactly = 1) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { oppgaveMock.opprettOppgave(any(), any()) }
        verify(exactly = 1) { repositoryMock.update(oppdatertKrav) }
    }
}
