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
import no.nav.helse.fritakagp.domain.generereEndretGravidKravBeskrivelse
import no.nav.helse.fritakagp.domain.generereGravidKravBeskrivelse
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Endring
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

class GravidKravEndreProcessor(
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
        val JOB_TYPE = "endre-gravid-krav"
        val dokumentasjonBrevkode = "endre_krav_om_fritak_fra_agp_dokumentasjon"
    }

    override val type: String get() = JOB_TYPE

    val digitalKravBehandingsType = "ae0121"
    val fritakAGPBehandingsTema = "ab0200"

    private val logger = this.logger()

    /**
     * Prosesserer endring av gravidkrav; journalfører og oppretter en oppgave for saksbehandler.
     * Jobbdataene forventes å være en UUID for et krav som skal prosesseres.
     */
    override fun prosesser(jobb: Bakgrunnsjobb) {
        val (forrigeKrav, oppdatertKrav) = getOrThrow(jobb)
        logger.info("Endrer krav ${forrigeKrav.id} til ${oppdatertKrav.id}")
        try {
            if (oppdatertKrav.virksomhetsnavn == null) {
                oppdatertKrav.virksomhetsnavn = forrigeKrav.virksomhetsnavn
            }
            oppdatertKrav.journalpostId = journalførOppdatering(oppdatertKrav, forrigeKrav)
            oppdatertKrav.oppgaveId = opprettOppgave(oppdatertKrav, forrigeKrav)
            bakgrunnsjobbRepo.save(
                Bakgrunnsjobb(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(BrukernotifikasjonJobbdata(oppdatertKrav.id, oppdatertKrav.identitetsnummer, oppdatertKrav.virksomhetsnavn, SkjemaType.GravidKrav, Endring)),
                    type = BrukernotifikasjonProcessorNy.JOB_TYPE
                )
            )
        } finally {
            updateAndLogOnFailure(oppdatertKrav)
        }
    }

    private fun getOrThrow(jobb: Bakgrunnsjobb): Pair<GravidKrav, GravidKrav> {
        val jobbData = om.readValue<GravidKravProcessor.JobbData>(jobb.data)
        val forrigeKrav = gravidKravRepo.getById(jobbData.id)
        requireNotNull(forrigeKrav) { "Jobben indikerte et krav med id ${jobb.data} men den kunne ikke finnes" }
        val oppdatertId = forrigeKrav.endretTilId
        requireNotNull(oppdatertId) { "Jobben indikerte et oppdatert krav men mangler id" }
        val oppdatertKrav = gravidKravRepo.getById(oppdatertId)
        requireNotNull(oppdatertKrav) { "Jobben indikerte et oppdatert krav med id $oppdatertId men den kunne ikke finnes" }
        return forrigeKrav to oppdatertKrav
    }

    override fun stoppet(jobb: Bakgrunnsjobb) {
        val (forrigeKrav, oppdatertKrav) = getOrThrow(jobb)

        val oppgaveId = opprettFordelingsOppgave(oppdatertKrav, forrigeKrav)
        logger.warn("Jobben ${jobb.uuid} feilet permanent og resulterte i fordelingsoppgave $oppgaveId")
    }

    private fun updateAndLogOnFailure(krav: GravidKrav) {
        try {
            gravidKravRepo.update(krav)
        } catch (e: Exception) {
            throw RuntimeException("Feilet i å oppdatere krav ${krav.id} etter at en ekstern operasjon har blitt utført. JournalpostID: ${krav.journalpostId} OppgaveID: ${krav.oppgaveId}", e)
        }
    }

    fun journalførOppdatering(oppdatertKrav: GravidKrav, forrigeKrav: GravidKrav): String {
        val journalfoeringsTittel = "Endring ${GravidKrav.tittel}"
        val id = runBlocking {
            val journalpostId = dokarkivKlient.opprettOgFerdigstillJournalpost(
                tittel = journalfoeringsTittel,
                gjelderPerson = GjelderPerson(oppdatertKrav.identitetsnummer),
                avsender = Avsender.Organisasjon(oppdatertKrav.virksomhetsnummer, oppdatertKrav.virksomhetsnavn ?: "Ukjent arbeidsgiver"),
                datoMottatt = oppdatertKrav.opprettet.toLocalDate(),
                dokumenter = createDocuments(oppdatertKrav, forrigeKrav, journalfoeringsTittel),
                eksternReferanseId = "${oppdatertKrav.id}-endring",
                callId = UUID.randomUUID().toString(),
                kanal = Kanal.NAV_NO
            )
            logger.debug("Journalført ${oppdatertKrav.id} med ref $journalpostId")
            return@runBlocking journalpostId.journalpostId
        }
        return id
    }

    private fun createDocuments(
        oppdatertKrav: GravidKrav,
        endretKrav: GravidKrav,
        journalfoeringsTittel: String
    ): List<Dokument> {
        val base64EnkodetPdf = Base64.getEncoder().encodeToString(pdfGenerator.lagEndringPdf(oppdatertKrav, endretKrav))
        val jsonOrginalDokument = Base64.getEncoder().encodeToString(om.writeValueAsBytes(listOf(endretKrav, oppdatertKrav)))
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

        bucketStorage.getDocAsString(endretKrav.id)?.let {
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

    fun opprettOppgave(oppdatertKrav: GravidKrav, forrigeKrav: GravidKrav): String {
        val aktoerId = pdlService.hentAktoerId(oppdatertKrav.identitetsnummer)
        requireNotNull(aktoerId) { "Fant ikke AktørID for fnr i ${oppdatertKrav.id}" }
        logger.info("Fant aktørid")

        val beskrivelse: String =
            buildString {
                append(generereGravidKravBeskrivelse(oppdatertKrav, "Endret: ${GravidKrav.tittel}"))
                appendLine()
                appendLine()
                append(generereEndretGravidKravBeskrivelse(forrigeKrav, "Tidligere: ${GravidKrav.tittel}"))
            }
        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = oppdatertKrav.journalpostId,
            beskrivelse = beskrivelse,
            tema = "SYK",
            behandlingstype = digitalKravBehandingsType,
            oppgavetype = "BEH_REF",
            behandlingstema = fritakAGPBehandingsTema,
            aktivDato = oppdatertKrav.opprettet.toLocalDate(),
            fristFerdigstillelse = oppdatertKrav.opprettet.plusDays(7).toLocalDate(),
            prioritet = "NORM"
        )

        return runBlocking { oppgaveKlient.opprettOppgave(request, UUID.randomUUID().toString()).id.toString() }
    }

    fun opprettFordelingsOppgave(oppdatertKrav: GravidKrav, forrigeKrav: GravidKrav): String {
        val aktoerId = pdlService.hentAktoerId(oppdatertKrav.identitetsnummer)
        requireNotNull(aktoerId) { "Fant ikke AktørID for fnr i ${oppdatertKrav.id}" }
        val beskrivelse: String =
            buildString {
                append(generereGravidKravBeskrivelse(oppdatertKrav, "Fordelingsoppgave for Endret: ${GravidKrav.tittel}"))
                append(generereEndretGravidKravBeskrivelse(forrigeKrav, "Tidligere: ${GravidKrav.tittel}"))
            }

        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = oppdatertKrav.journalpostId,
            beskrivelse = beskrivelse,
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
