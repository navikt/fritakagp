package no.nav.helse.fritakagp.processing.gravid.soeknad

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.fritakagp.GravidSoeknadMetrics
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.domain.GravidSoeknad
import no.nav.helse.fritakagp.domain.generereGravidSoeknadBeskrivelse
import no.nav.helse.fritakagp.integration.IBrregService
import no.nav.helse.fritakagp.integration.PdlService
import no.nav.helse.fritakagp.integration.arbeidsgiver.OPPGAVETYPE_FORDELINGSOPPGAVE
import no.nav.helse.fritakagp.integration.arbeidsgiver.OppgaveKlient
import no.nav.helse.fritakagp.integration.arbeidsgiver.OpprettOppgaveRequest
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Oppretting
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.SkjemaType.GravidSøknad
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessorNy
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.dokarkiv.domene.Avsender
import no.nav.helsearbeidsgiver.dokarkiv.domene.Dokument
import no.nav.helsearbeidsgiver.dokarkiv.domene.DokumentVariant
import no.nav.helsearbeidsgiver.dokarkiv.domene.GjelderPerson
import no.nav.helsearbeidsgiver.dokarkiv.domene.Kanal
import no.nav.helsearbeidsgiver.utils.log.logger
import java.time.LocalDate
import java.util.Base64
import java.util.UUID

class GravidSoeknadProcessor(
    private val gravidSoeknadRepo: GravidSoeknadRepository,
    private val dokarkivKlient: DokArkivClient,
    private val oppgaveKlient: OppgaveKlient,
    private val pdlService: PdlService,
    private val bakgrunnsjobbRepo: BakgrunnsjobbRepository,
    private val pdfGenerator: GravidSoeknadPDFGenerator,
    private val om: ObjectMapper,
    private val bucketStorage: BucketStorage,
    private val brregService: IBrregService
) : BakgrunnsjobbProsesserer {
    companion object {
        const val JOB_TYPE = "gravid-søknad-formidling"
        const val dokumentasjonBrevkode = "soeknad_om_fritak_fra_agp_dokumentasjon"
        const val brevkode = "soeknad_om_fritak_fra_agp_gravid"
    }

    override val type: String get() = JOB_TYPE

    val digitalSoeknadBehandingsType = "ae0227"
    val fritakAGPBehandingsTema = "ab0338"

    private val logger = this.logger()

    /**
     * Prosesserer en gravidsøknad; journalfører søknaden og oppretter en oppgave for saksbehandler.
     * Jobbdataene forventes å være en UUID for en søknad som skal prosesseres.
     */
    override fun prosesser(jobb: Bakgrunnsjobb) {
        val jobbData = om.readValue<JobbData>(jobb.data)
        val soeknad = gravidSoeknadRepo.getById(jobbData.id)
        requireNotNull(soeknad) { "Jobben indikerte en søknad med id ${jobb.data} men den kunne ikke finnes" }

        try {
            if (soeknad.virksomhetsnavn == null) {
                runBlocking {
                    soeknad.virksomhetsnavn = brregService.hentOrganisasjonNavn(soeknad.virksomhetsnummer)
                }
            }
            if (soeknad.journalpostId == null) {
                soeknad.journalpostId = journalfør(soeknad)
                GravidSoeknadMetrics.tellJournalfoert()
            }

            bucketStorage.deleteDoc(soeknad.id)

            if (soeknad.oppgaveId == null) {
                soeknad.oppgaveId = opprettOppgave(soeknad)
                GravidSoeknadMetrics.tellOppgaveOpprettet()
            }

            bakgrunnsjobbRepo.save(
                Bakgrunnsjobb(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(BrukernotifikasjonJobbdata(soeknad.id, soeknad.identitetsnummer, soeknad.virksomhetsnavn, GravidSøknad, Oppretting)),
                    type = BrukernotifikasjonProcessorNy.JOB_TYPE
                )
            )
        } finally {
            updateAndLogOnFailure(soeknad)
        }
    }

    private fun updateAndLogOnFailure(soeknad: GravidSoeknad) {
        try {
            gravidSoeknadRepo.update(soeknad)
        } catch (e: Exception) {
            throw RuntimeException("Feilet i å lagre ${soeknad.id} etter at en ekstern operasjon har blitt utført. JournalpostID: ${soeknad.journalpostId} OppgaveID: ${soeknad.oppgaveId}", e)
        }
    }

    fun journalfør(soeknad: GravidSoeknad): String {
        val id = runBlocking {
            val journalpostId = dokarkivKlient.opprettOgFerdigstillJournalpost(
                tittel = GravidSoeknad.tittel,
                gjelderPerson = GjelderPerson(soeknad.identitetsnummer),
                avsender = Avsender.Organisasjon(soeknad.virksomhetsnummer, soeknad.virksomhetsnavn ?: "Ukjent arbeidsgiver"),
                datoMottatt = soeknad.opprettet.toLocalDate(),
                dokumenter = createDocuments(soeknad, GravidSoeknad.tittel),
                eksternReferanseId = soeknad.id.toString(),
                callId = UUID.randomUUID().toString(),
                kanal = Kanal.NAV_NO
            )
            logger.debug("Journalført ${soeknad.id} med ref $journalpostId")
            return@runBlocking journalpostId.journalpostId
        }
        return id
    }

    /**
     * Når vi gir opp, opprette en fordelingsoppgave til saksbehandler
     */
    override fun stoppet(jobb: Bakgrunnsjobb) {
        val soeknad = getSoeknadOrThrow(jobb)
        val oppgaveId = opprettFordelingsOppgave(soeknad)
        logger.warn("Jobben ${jobb.uuid} feilet permanent og resulterte i fordelingsoppgave $oppgaveId")
    }

    private fun getSoeknadOrThrow(jobb: Bakgrunnsjobb): GravidSoeknad {
        val jobbData = om.readValue<JobbData>(jobb.data)
        val soeknad = gravidSoeknadRepo.getById(jobbData.id)
        requireNotNull(soeknad) { "Jobben indikerte en søknad med id ${jobb.data} men den kunne ikke finnes" }
        return soeknad
    }

    private fun createDocuments(
        soeknad: GravidSoeknad,
        journalfoeringsTittel: String
    ): List<Dokument> {
        val base64EnkodetPdf = Base64.getEncoder().encodeToString(pdfGenerator.lagPDF(soeknad))
        val jsonOrginalDokument = Base64.getEncoder().encodeToString(om.writeValueAsBytes(soeknad))
        val dokumentListe = mutableListOf(
            Dokument(
                dokumentVarianter = listOf(
                    DokumentVariant(
                        fysiskDokument = base64EnkodetPdf,
                        filtype = "PDF",
                        variantFormat = "ARKIV",
                        filnavn = null
                    )
                ),
                brevkode = brevkode,
                tittel = journalfoeringsTittel
            )
        )

        bucketStorage.getDocAsString(soeknad.id)?.let {
            dokumentListe.add(
                Dokument(
                    dokumentVarianter = listOf(
                        DokumentVariant(
                            fysiskDokument = it.base64Data,
                            filtype = it.extension.uppercase(),
                            variantFormat = "ARKIV",
                            filnavn = null
                        ),
                        DokumentVariant(
                            variantFormat = "ORIGINAL",
                            fysiskDokument = jsonOrginalDokument,
                            filtype = "JSON",
                            filnavn = null
                        )
                    ),
                    brevkode = dokumentasjonBrevkode,
                    tittel = "Helsedokumentasjon"
                )
            )
        }

        return dokumentListe
    }

    fun opprettOppgave(soeknad: GravidSoeknad): String {
        val aktoerId = pdlService.hentAktoerId(soeknad.identitetsnummer)
        requireNotNull(aktoerId) { "Fant ikke AktørID for fnr i ${soeknad.id}" }

        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = soeknad.journalpostId,
            beskrivelse = generereGravidSoeknadBeskrivelse(soeknad, GravidSoeknad.tittel),
            tema = "SYK",
            behandlingstype = digitalSoeknadBehandingsType,
            oppgavetype = "BEH_SAK",
            behandlingstema = fritakAGPBehandingsTema,
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = LocalDate.now().plusDays(7),
            prioritet = "NORM"
        )

        return runBlocking {
            oppgaveKlient.opprettOppgave(request, UUID.randomUUID().toString()).id.toString()
        }
    }

    fun opprettFordelingsOppgave(soeknad: GravidSoeknad): String {
        val aktoerId = pdlService.hentAktoerId(soeknad.identitetsnummer)
        requireNotNull(aktoerId) { "Fant ikke AktørID for fnr i ${soeknad.id}" }

        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = soeknad.journalpostId,
            beskrivelse = generereGravidSoeknadBeskrivelse(soeknad, "Fordelingsoppgave for ${GravidSoeknad.tittel}"),
            tema = "SYK",
            behandlingstype = digitalSoeknadBehandingsType,
            oppgavetype = OPPGAVETYPE_FORDELINGSOPPGAVE,
            behandlingstema = fritakAGPBehandingsTema,
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = LocalDate.now().plusDays(7),
            prioritet = "NORM"
        )

        return runBlocking { oppgaveKlient.opprettOppgave(request, UUID.randomUUID().toString()).id.toString() }
    }

    data class JobbData(val id: UUID)
}
