package no.nav.helse.fritakagp.processing.gravid.krav

import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.GravidTestData
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.integration.BrregService
import no.nav.helse.fritakagp.integration.PdlService
import no.nav.helse.fritakagp.integration.arbeidsgiver.OPPGAVETYPE_FORDELINGSOPPGAVE
import no.nav.helse.fritakagp.integration.arbeidsgiver.OppgaveKlient
import no.nav.helse.fritakagp.integration.arbeidsgiver.OpprettOppgaveRequest
import no.nav.helse.fritakagp.integration.gcp.BucketDocument
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.jsonEquals
import no.nav.helse.fritakagp.koin.loadFromResources
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils.emptyJob
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils.testJob
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessorNy
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravProcessor.Companion.brevkode
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravProcessor.Companion.dokumentasjonBrevkode
import no.nav.helse.fritakagp.readToObjectNode
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.OpprettOgFerdigstillResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import java.util.Base64

class GravidKravProcessorTest {

    val joarkMock = mockk<DokArkivClient>(relaxed = true)
    val oppgaveMock = mockk<OppgaveKlient>(relaxed = true)
    val repositoryMock = mockk<GravidKravRepository>(relaxed = true)
    val pdlServiceMock = mockk<PdlService>(relaxed = true)
    val objectMapper = customObjectMapper()
    val pdfGeneratorMock = mockk<GravidKravPDFGenerator>(relaxed = true)
    val bucketStorageMock = mockk<BucketStorage>(relaxed = true)
    val bakgrunnsjobbRepomock = mockk<BakgrunnsjobbRepository>(relaxed = true)
    val brregServiceMock = mockk<BrregService>()

    val prosessor = GravidKravProcessor(repositoryMock, joarkMock, oppgaveMock, pdlServiceMock, bakgrunnsjobbRepomock, pdfGeneratorMock, objectMapper, bucketStorageMock, brregServiceMock)
    lateinit var krav: GravidKrav

    private val oppgaveId = 9999
    private val arkivReferanse = "12345"
    private var jobb = emptyJob()

    @BeforeEach
    fun setup() {
        krav = GravidTestData.gravidKrav.copy()
        jobb = testJob(objectMapper.writeValueAsString(GravidKravProcessor.JobbData(krav.id)))

        every { repositoryMock.getById(krav.id) } returns krav
        every { bucketStorageMock.getDocAsString(any()) } returns null
        every { pdlServiceMock.hentAktoerId(krav.identitetsnummer) } returns "aktør-id"
        coEvery { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns OpprettOgFerdigstillResponse(arkivReferanse, true, null, emptyList())
        coEvery { oppgaveMock.opprettOppgave(any(), any()) } returns GravidTestData.gravidOpprettOppgaveResponse.copy(id = oppgaveId)
        coEvery { brregServiceMock.hentOrganisasjonNavn(krav.virksomhetsnummer) } returns "Stark Industries"
    }

    @Test
    fun `skal ikke journalføre når det allerede foreligger en journalpostId, men skal forsøke sletting fra bucket `() {
        krav.journalpostId = "joark"
        prosessor.prosesser(jobb)

        coVerify(exactly = 0) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { bucketStorageMock.deleteDoc(krav.id) }
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
        every { bucketStorageMock.getDocAsString(krav.id) } returns BucketDocument(dokumentData, filtypeArkiv)

        coEvery { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) } returns OpprettOgFerdigstillResponse(arkivReferanse, true, "M", emptyList())

        Base64.getEncoder().encodeToString(objectMapper.writeValueAsBytes(krav))
        prosessor.prosesser(jobb)

        verify(exactly = 1) { bucketStorageMock.getDocAsString(krav.id) }
        verify(exactly = 1) { bucketStorageMock.deleteDoc(krav.id) }

        coVerify(exactly = 1) {
            joarkMock.opprettOgFerdigstillJournalpost(
                GravidKrav.tittel,
                any(),
                any(),
                any(),
                withArg {
                    assertEquals(2, it.size)
                    assertEquals(brevkode, it.first().brevkode)
                    assertEquals(dokumentasjonBrevkode, it[1].brevkode)
                    assertEquals("ARKIV", it[0].dokumentVarianter[0].variantFormat)
                    assertEquals("PDF", it[0].dokumentVarianter[0].filtype)
                    assertEquals("JSON", it[0].dokumentVarianter[1].filtype)
                    assertEquals("ORIGINAL", it[0].dokumentVarianter[1].variantFormat)
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
        krav.oppgaveId = "ppggssv"
        prosessor.prosesser(jobb)
        coVerify(exactly = 0) { oppgaveMock.opprettOppgave(any(), any()) }
    }

    @Test
    fun `skal journalføre, opprette oppgave og oppdatere søknaden i databasen`() {
        val forventetJson = "gravidKravRobotBeskrivelse.json".loadFromResources()

        prosessor.prosesser(jobb)

        assertThat(krav.journalpostId).isEqualTo(arkivReferanse)
        assertThat(krav.oppgaveId).isEqualTo(oppgaveId.toString())

        coVerify(exactly = 1) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) {
            oppgaveMock.opprettOppgave(
                withArg {
                    assertEquals("ROB_BEH", it.oppgavetype)
                    if (!forventetJson.jsonEquals(objectMapper, it.beskrivelse!!, "id", "opprettet")) {
                        println("expected json to be equal, was not: \nexpectedJson=$forventetJson \nactualJson=${it.beskrivelse}")
                        fail()
                    } else if (!forventetJson.readToObjectNode(objectMapper)["kravType"].asText().equals("GRAVID")) {
                        println("expected json to contain kravType = GRAVID, was not")
                        fail()
                    }
                },
                any()
            )
        }

        verify(exactly = 1) { repositoryMock.update(krav) }
    }

    @Test
    fun `skal opprette jobber`() {
        prosessor.prosesser(jobb)

        val opprettetJobber = mutableListOf<Bakgrunnsjobb>()

        verify(exactly = 1) {
            bakgrunnsjobbRepomock.save(capture(opprettetJobber))
        }

        val beskjedJobb = opprettetJobber.find { it.type == BrukernotifikasjonProcessorNy.JOB_TYPE }
        assertThat(beskjedJobb?.data).contains(BrukernotifikasjonJobbdata.SkjemaType.GravidKrav.name)
        assertThat(beskjedJobb?.data).contains(krav.id.toString())
    }

    @Test
    fun `Ved feil i oppgave skal joarkref lagres, og det skal det kastes exception oppover`() {
        coEvery { oppgaveMock.opprettOppgave(any(), any()) } throws IOException()

        assertThrows<IOException> { prosessor.prosesser(jobb) }

        assertThat(krav.journalpostId).isEqualTo(arkivReferanse)
        assertThat(krav.oppgaveId).isNull()

        coVerify(exactly = 1) { joarkMock.opprettOgFerdigstillJournalpost(any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { oppgaveMock.opprettOppgave(any(), any()) }
        verify(exactly = 1) { repositoryMock.update(krav) }
    }
}
