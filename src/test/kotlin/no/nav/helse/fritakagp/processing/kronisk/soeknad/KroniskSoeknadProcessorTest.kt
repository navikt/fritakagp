package no.nav.helse.fritakagp.processing.kronisk.soeknad

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.KroniskTestData
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OPPGAVETYPE_FORDELINGSOPPGAVE
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OpprettOppgaveRequest
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.domain.KroniskSoeknad
import no.nav.helse.fritakagp.integration.gcp.BucketDocument
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils.emptyJob
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils.testJob
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.SkjemaType
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessorNy
import no.nav.helse.fritakagp.service.PdlService
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.Base64
import kotlin.test.assertEquals

class KroniskSoeknadProcessorTest {

    val joarkMock = mockk<DokArkivClient>(relaxed = true)
    val oppgaveMock = mockk<OppgaveKlient>(relaxed = true)
    val repositoryMock = mockk<KroniskSoeknadRepository>(relaxed = true)
    val pdlServiceMock = mockk<PdlService>(relaxed = true)
    val objectMapper = customObjectMapper()
    val pdfGeneratorMock = mockk<KroniskSoeknadPDFGenerator>(relaxed = true)
    val bucketStorageMock = mockk<BucketStorage>(relaxed = true)
    val bakgrunnsjobbRepomock = mockk<BakgrunnsjobbRepository>(relaxed = true)
    val brregClientMock = mockk<BrregClient>()
    val prosessor = KroniskSoeknadProcessor(
        repositoryMock,
        joarkMock,
        oppgaveMock,
        bakgrunnsjobbRepomock,
        pdlServiceMock,
        pdfGeneratorMock,
        objectMapper,
        bucketStorageMock,
        brregClientMock
    )
    lateinit var soeknad: KroniskSoeknad

    private val oppgaveId = 9999
    private val arkivReferanse = "12345"
    private var jobb = emptyJob()

    @BeforeEach
    fun setup() {
        soeknad = KroniskTestData.soeknadKronisk.copy()
        val orgnr = Orgnr(soeknad.virksomhetsnummer)

        jobb = testJob(objectMapper.writeValueAsString(KroniskSoeknadProcessor.JobbData(soeknad.id)))
        objectMapper.registerModule(JavaTimeModule())

        every { repositoryMock.getById(soeknad.id) } returns soeknad
        every { bucketStorageMock.getDocAsString(any()) } returns null
        every { pdlServiceMock.hentAktoerId(soeknad.identitetsnummer) } returns "aktør-id"
        coEvery { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns OpprettOgFerdigstillResponse(arkivReferanse, true, null, emptyList())
        coEvery { oppgaveMock.opprettOppgave(any(), any()) } returns KroniskTestData.kroniskOpprettOppgaveResponse.copy(id = oppgaveId)
        coEvery { brregClientMock.hentOrganisasjonNavn(setOf(orgnr.verdi)) } returns mapOf(orgnr to "Stark Industries")
    }

    @Test
    fun `skal ikke journalføre når det allerede foreligger en journalpostId, men skal forsøke sletting fra bucket `() {
        soeknad.journalpostId = "joark"
        prosessor.prosesser(jobb)

        coVerify(exactly = 0) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { bucketStorageMock.deleteDoc(soeknad.id) }
    }

    @Test
    fun `skal opprette fordelingsoppgave når stoppet`() {
        prosessor.stoppet(jobb)

        val oppgaveRequest = CapturingSlot<OpprettOppgaveRequest>()

        coVerify(exactly = 1) { oppgaveMock.opprettOppgave(capture(oppgaveRequest), any()) }
        assertThat(oppgaveRequest.captured.oppgavetype).isEqualTo(OPPGAVETYPE_FORDELINGSOPPGAVE)
    }

    @Test
    fun `Om det finnes ekstra dokumentasjon skal den journalføres og så slettes`() {
        val dokumentData = "test"
        val filtypeArkiv = "pdf"
        every { bucketStorageMock.getDocAsString(soeknad.id) } returns BucketDocument(dokumentData, filtypeArkiv)

        coEvery { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns OpprettOgFerdigstillResponse(arkivReferanse, true, null, emptyList())

        Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(soeknad))
        prosessor.prosesser(jobb)

        verify(exactly = 1) { bucketStorageMock.getDocAsString(soeknad.id) }
        verify(exactly = 1) { bucketStorageMock.deleteDoc(soeknad.id) }

        coVerify(exactly = 1) {
            joarkMock.opprettOgFerdigstillJournalpost(
                KroniskSoeknad.tittel,
                any(),
                any(),
                any(),
                withArg {
                    assertEquals(2, it.size)
                    assertEquals(KroniskSoeknadProcessor.brevkode, it.first().brevkode)
                    assertEquals(KroniskSoeknadProcessor.dokumentasjonBrevkode, it[1].brevkode)
                    assertEquals("ARKIV", it[0].dokumentVarianter[0].variantFormat)
                    assertEquals("PDF", it[0].dokumentVarianter[0].filtype)
                    assertEquals(dokumentData, it[1].dokumentVarianter[0].fysiskDokument)
                    assertEquals("ARKIV", it[1].dokumentVarianter[0].variantFormat)
                    assertEquals("ORIGINAL", it[1].dokumentVarianter[1].variantFormat)
                },
                any(),
                any(),
                any()
            )
        }
    }

    @Test
    fun `skal ikke lage oppgave når det allerede foreligger en oppgaveId `() {
        soeknad.oppgaveId = "ppggssv"
        prosessor.prosesser(jobb)
        coVerify(exactly = 0) { oppgaveMock.opprettOppgave(any(), any()) }
    }

    @Test
    fun `skal journalføre, opprette oppgave og oppdatere søknaden i databasen`() {
        prosessor.prosesser(jobb)

        assertThat(soeknad.journalpostId).isEqualTo(arkivReferanse)
        assertThat(soeknad.oppgaveId).isEqualTo(oppgaveId.toString())

        coVerify(exactly = 1) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { oppgaveMock.opprettOppgave(any(), any()) }
        verify(exactly = 1) { repositoryMock.update(soeknad) }
    }

    @Test
    fun `skal opprette jobber`() {
        prosessor.prosesser(jobb)

        val opprettetJobber = mutableListOf<Bakgrunnsjobb>()

        verify(exactly = 1) {
            bakgrunnsjobbRepomock.save(capture(opprettetJobber))
        }

        val beskjedJobb = opprettetJobber.find { it.type == BrukernotifikasjonProcessorNy.JOB_TYPE }
        assertThat(beskjedJobb?.data).contains(soeknad.id.toString())
        assertThat(beskjedJobb?.data).contains(SkjemaType.KroniskSøknad.name)
    }

    @Test
    fun `Ved feil i oppgave skal joarkref lagres, og det skal det kastes exception oppover`() {
        coEvery { oppgaveMock.opprettOppgave(any(), any()) } throws IOException()

        assertThrows<IOException> { prosessor.prosesser(jobb) }

        assertThat(soeknad.journalpostId).isEqualTo(arkivReferanse)
        assertThat(soeknad.oppgaveId).isNull()

        coVerify(exactly = 1) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { oppgaveMock.opprettOppgave(any(), any()) }
        verify(exactly = 1) { repositoryMock.update(soeknad) }
    }
}
