package no.nav.helse.fritakagp.processing.gravid.krav

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OPPGAVETYPE_FORDELINGSOPPGAVE
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OpprettOppgaveRequest
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.generereSlettGravidKravBeskrivelse
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Annullering
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.SkjemaType
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessorNy
import no.nav.helse.fritakagp.service.PdlService
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

class GravidKravSlettProcessor(
    private val gravidKravRepo: GravidKravRepository,
    private val dokarkivKlient: DokArkivClient,
    private val oppgaveKlient: OppgaveKlient,
    private val pdlService: PdlService,
    private val pdfGenerator: GravidKravPDFGenerator,
    private val om: ObjectMapper,
    private val bucketStorage: BucketStorage,
    private val bakgrunnsjobbRepo: BakgrunnsjobbRepository
) : BakgrunnsjobbProsesserer {
    companion object {
        const val JOB_TYPE = "slett-gravid-krav"
        const val dokumentasjonBrevkode = "annuler_krav_om_fritak_fra_agp_dokumentasjon"
    }

    override val type: String get() = JOB_TYPE

    val digitalKravBehandingsType = "ae0121"
    val fritakAGPBehandingsTema = "ab0200"

    private val logger = this.logger()

    /**
     * Prosesserer sletting av  gravidkrav; journalfører og oppretter en oppgave for saksbehandler.
     * Jobbdataene forventes å være en UUID for et krav som skal prosesseres.
     */
    override fun prosesser(jobb: Bakgrunnsjobb) {
        val krav = getOrThrow(jobb)
        logger.info("Sletter krav ${krav.id}")
        try {
            krav.sletteJournalpostId = journalførSletting(krav)
            krav.oppgaveId = opprettOppgave(krav)
            bakgrunnsjobbRepo.save(
                Bakgrunnsjobb(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(BrukernotifikasjonJobbdata(krav.id, krav.identitetsnummer, krav.virksomhetsnavn, SkjemaType.GravidKrav, Annullering)),
                    type = BrukernotifikasjonProcessorNy.JOB_TYPE
                )
            )
        } finally {
            updateAndLogOnFailure(krav)
        }
    }

    private fun getOrThrow(jobb: Bakgrunnsjobb): GravidKrav {
        val jobbData = om.readValue<GravidKravProcessor.JobbData>(jobb.data)
        val krav = gravidKravRepo.getById(jobbData.id)
        requireNotNull(krav) { "Jobben indikerte et krav med id ${jobb.data} men den kunne ikke finnes" }
        return krav
    }

    override fun stoppet(jobb: Bakgrunnsjobb) {
        val krav = getOrThrow(jobb)
        val oppgaveId = opprettFordelingsOppgave(krav)
        logger.warn("Jobben ${jobb.uuid} feilet permanent og resulterte i fordelingsoppgave $oppgaveId")
    }

    private fun updateAndLogOnFailure(krav: GravidKrav) {
        try {
            gravidKravRepo.update(krav)
        } catch (e: Exception) {
            throw RuntimeException("Feilet i å oppdatere slettet krav ${krav.id} etter at en ekstern operasjon har blitt utført. JournalpostID: ${krav.journalpostId} OppgaveID: ${krav.oppgaveId}", e)
        }
    }

    fun journalførSletting(krav: GravidKrav): String {
        val journalfoeringsTittel = "Annuller ${GravidKrav.tittel}"
        val id = runBlocking {
            val journalpostId = dokarkivKlient.opprettOgFerdigstillJournalpost(
                tittel = journalfoeringsTittel,
                gjelderPerson = GjelderPerson(krav.identitetsnummer),
                avsender = Avsender.Organisasjon(krav.virksomhetsnummer, krav.virksomhetsnavn ?: "Ukjent arbeidsgiver"),
                datoMottatt = krav.opprettet.toLocalDate(),
                dokumenter = createDocuments(krav, journalfoeringsTittel),
                eksternReferanseId = "${krav.id}-annul",
                callId = UUID.randomUUID().toString(),
                kanal = Kanal.NAV_NO
            )
            logger.debug("Journalført ${krav.id} med ref $journalpostId")
            return@runBlocking journalpostId.journalpostId
        }
        return id
    }

    private fun createDocuments(
        krav: GravidKrav,
        journalfoeringsTittel: String
    ): List<Dokument> {
        val base64EnkodetPdf = Base64.getEncoder().encodeToString(pdfGenerator.lagSlettingPDF(krav))
        val jsonOrginalDokument = Base64.getEncoder().encodeToString(om.writeValueAsBytes(krav))
        val dokumentListe = mutableListOf(
            Dokument(
                dokumentVarianter = listOf(
                    DokumentVariant(
                        fysiskDokument = base64EnkodetPdf,
                        filtype = "PDF",
                        variantFormat = "ARKIV",
                        filnavn = null
                    ),
                    DokumentVariant(
                        filtype = "JSON",
                        fysiskDokument = jsonOrginalDokument,
                        variantFormat = "ORIGINAL",
                        filnavn = null
                    )
                ),
                brevkode = dokumentasjonBrevkode,
                tittel = journalfoeringsTittel
            )
        )

        bucketStorage.getDocAsString(krav.id)?.let {
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
                            filtype = "JSON",
                            fysiskDokument = jsonOrginalDokument,
                            variantFormat = "ORIGINAL",
                            filnavn = null
                        )
                    ),
                    brevkode = GravidKravProcessor.dokumentasjonBrevkode,
                    tittel = "Helsedokumentasjon"
                )
            )
        }

        return dokumentListe
    }

    fun opprettOppgave(krav: GravidKrav): String {
        val aktoerId = pdlService.hentAktoerId(krav.identitetsnummer)
        requireNotNull(aktoerId) { "Fant ikke AktørID for fnr i ${krav.id}" }
        logger.info("Fant aktørid")
        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = krav.journalpostId,
            beskrivelse = generereSlettGravidKravBeskrivelse(krav, "Annullering av ${GravidKrav.tittel}."),
            tema = "SYK",
            behandlingstype = digitalKravBehandingsType,
            oppgavetype = "BEH_REF",
            behandlingstema = fritakAGPBehandingsTema,
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = LocalDate.now().plusDays(7),
            prioritet = "NORM"
        )

        return runBlocking { oppgaveKlient.opprettOppgave(request, UUID.randomUUID().toString()).id.toString() }
    }

    fun opprettFordelingsOppgave(krav: GravidKrav): String {
        val aktoerId = pdlService.hentAktoerId(krav.identitetsnummer)
        requireNotNull(aktoerId) { "Fant ikke AktørID for fnr i ${krav.id}" }

        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = krav.journalpostId,
            beskrivelse = generereSlettGravidKravBeskrivelse(krav, "Fordelingsoppgave for annullering av ${GravidKrav.tittel}"),
            tema = "SYK",
            behandlingstype = digitalKravBehandingsType,
            oppgavetype = OPPGAVETYPE_FORDELINGSOPPGAVE,
            behandlingstema = fritakAGPBehandingsTema,
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = LocalDate.now().plusDays(7),
            prioritet = "NORM"
        )

        return runBlocking { oppgaveKlient.opprettOppgave(request, UUID.randomUUID().toString()).id.toString() }
    }
}
