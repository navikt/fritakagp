package no.nav.helse.fritakagp.processing.kronisk.soeknad

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OPPGAVETYPE_FORDELINGSOPPGAVE
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OpprettOppgaveRequest
import no.nav.helse.fritakagp.KroniskSoeknadMetrics
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.domain.KroniskSoeknad
import no.nav.helse.fritakagp.domain.generereKroniskSoeknadBeskrivelse
import no.nav.helse.fritakagp.integration.brreg.BrregClient
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Oppretting
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.SkjemaType.KroniskSøknad
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

class KroniskSoeknadProcessor(
    private val kroniskSoeknadRepo: KroniskSoeknadRepository,
    private val dokarkivKlient: DokArkivClient,
    private val oppgaveKlient: OppgaveKlient,
    private val bakgrunnsjobbRepo: BakgrunnsjobbRepository,
    private val pdlService: PdlService,
    private val pdfGenerator: KroniskSoeknadPDFGenerator,
    private val om: ObjectMapper,
    private val bucketStorage: BucketStorage,
    private val brregClient: BrregClient
) : BakgrunnsjobbProsesserer {
    companion object {
        const val dokumentasjonBrevkode = "soeknad_om_fritak_fra_agp_dokumentasjon"
        const val brevkode = "soeknad_om_fritak_fra_agp_kronisk"
    }

    override val type: String get() = "kronisk-søknad-formidling"

    val digitalSoeknadBehandingsType = "ae0227"
    val fritakAGPBehandingsTema = "ab0338"

    private val logger = this.logger()

    /**
     * Prosesserer en kronisksøknad; journalfører søknaden og oppretter en oppgave for saksbehandler.
     * Jobbdataene forventes å være en UUID for en søknad som skal prosesseres.
     */
    override fun prosesser(jobb: Bakgrunnsjobb) {
        val soeknad = getSoeknadOrThrow(jobb)

        try {
            if (soeknad.virksomhetsnavn == null) {
                runBlocking {
                    soeknad.virksomhetsnavn = brregClient.getVirksomhetsNavn(soeknad.virksomhetsnummer)
                }
            }
            if (soeknad.journalpostId == null) {
                soeknad.journalpostId = journalfør(soeknad)
                KroniskSoeknadMetrics.tellJournalfoert()
            }

            bucketStorage.deleteDoc(soeknad.id)

            if (soeknad.oppgaveId == null) {
                soeknad.oppgaveId = opprettOppgave(soeknad)
                KroniskSoeknadMetrics.tellOppgaveOpprettet()
            }

            bakgrunnsjobbRepo.save(
                Bakgrunnsjobb(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(BrukernotifikasjonJobbdata(soeknad.id, soeknad.identitetsnummer, soeknad.virksomhetsnavn, KroniskSøknad, Oppretting)),
                    type = BrukernotifikasjonProcessorNy.JOB_TYPE
                )
            )
        } finally {
            updateAndLogOnFailure(soeknad)
        }
    }

    /**
     * Når vi gir opp, opprette en fordelingsoppgave til saksbehandler
     */
    override fun stoppet(jobb: Bakgrunnsjobb) {
        val soeknad = getSoeknadOrThrow(jobb)
        val oppgaveId = opprettFordelingsOppgave(soeknad)
        logger.warn("Jobben ${jobb.uuid} feilet permanent og resulterte i fordelingsoppgave $oppgaveId")
    }

    private fun getSoeknadOrThrow(jobb: Bakgrunnsjobb): KroniskSoeknad {
        val jobbData = om.readValue<JobbData>(jobb.data)
        val soeknad = kroniskSoeknadRepo.getById(jobbData.id)
        requireNotNull(soeknad) { "Jobben indikerte en søknad med id ${jobb.data} men den kunne ikke finnes" }
        return soeknad
    }

    private fun updateAndLogOnFailure(soeknad: KroniskSoeknad) {
        try {
            kroniskSoeknadRepo.update(soeknad)
        } catch (e: Exception) {
            logger.error("Feilet i å lagre ${soeknad.id} etter at en ekstern operasjon har blitt utført. JournalpostID: ${soeknad.journalpostId} OppgaveID: ${soeknad.oppgaveId}")
            throw e
        }
    }

    fun journalfør(soeknad: KroniskSoeknad): String {
        val id = runBlocking {
            val journalpostId = dokarkivKlient.opprettOgFerdigstillJournalpost(
                tittel = KroniskSoeknad.tittel,
                gjelderPerson = GjelderPerson(soeknad.identitetsnummer),
                avsender = Avsender.Organisasjon(soeknad.virksomhetsnummer, soeknad.virksomhetsnavn ?: "Ukjent arbeidsgiver"),
                datoMottatt = soeknad.opprettet.toLocalDate(),
                dokumenter = createDocuments(soeknad, KroniskSoeknad.tittel),
                eksternReferanseId = soeknad.id.toString(),
                callId = UUID.randomUUID().toString(),
                kanal = Kanal.NAV_NO
            )
            logger.debug("Journalført ${soeknad.id} med ref $journalpostId")
            return@runBlocking journalpostId.journalpostId
        }
        return id
    }

    fun createDocuments(
        soeknad: KroniskSoeknad,
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
                            filnavn = null,
                            variantFormat = "ARKIV"
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

    fun opprettOppgave(soeknad: KroniskSoeknad): String {
        val aktoerId = pdlService.hentAktoerId(soeknad.identitetsnummer)
        requireNotNull(aktoerId) { "Fant ikke AktørID for fnr i ${soeknad.id}" }

        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = soeknad.journalpostId,
            beskrivelse = generereKroniskSoeknadBeskrivelse(soeknad, KroniskSoeknad.tittel),
            tema = "SYK",
            behandlingstype = digitalSoeknadBehandingsType,
            oppgavetype = "BEH_SAK",
            behandlingstema = fritakAGPBehandingsTema,
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = LocalDate.now().plusDays(7),
            prioritet = "NORM"
        )

        return runBlocking { oppgaveKlient.opprettOppgave(request, UUID.randomUUID().toString()).id.toString() }
    }

    fun opprettFordelingsOppgave(soeknad: KroniskSoeknad): String {
        val aktoerId = pdlService.hentAktoerId(soeknad.identitetsnummer)
        requireNotNull(aktoerId) { "Fant ikke AktørID for fnr i ${soeknad.id}" }

        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = soeknad.journalpostId,
            beskrivelse = generereKroniskSoeknadBeskrivelse(soeknad, "Fordelingsoppgave for ${KroniskSoeknad.tittel}"),
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
