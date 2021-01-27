package no.nav.helse.fritakagp.processing.kronisk.krav

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.helse.arbeidsgiver.integrasjoner.dokarkiv.*
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave.OpprettOppgaveRequest
import no.nav.helse.arbeidsgiver.integrasjoner.pdl.PdlClient
import no.nav.helse.arbeidsgiver.integrasjoner.pdl.PdlIdent
import no.nav.helse.fritakagp.KroniskKravMetrics
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKafkaProcessor
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class KroniskKravProcessor(
    private val kroniskKravRepo: KroniskKravRepository,
    private val dokarkivKlient: DokarkivKlient,
    private val oppgaveKlient: OppgaveKlient,
    private val pdlClient: PdlClient,
    private val bakgrunnsjobbRepo : BakgrunnsjobbRepository,
    private val pdfGenerator: KroniskKravPDFGenerator,
    private val om: ObjectMapper,
    private val bucketStorage: BucketStorage
) : BakgrunnsjobbProsesserer {
    companion object {
        val JOB_TYPE = "kronisk-krav-formidling"
        val dokumentasjonBrevkode = "krav_om_fritak_fra_agp_dokumentasjon"
    }

    val digitalKravBehandingsType = "ae0227"
    val fritakAGPBehandingsTema = "ab0338"

    val log = LoggerFactory.getLogger(KroniskKravProcessor::class.java)

    /**
     * Prosesserer et kroniskkrav; journalfører kravet og oppretter en oppgave for saksbehandler.
     * Jobbdataene forventes å være en UUID for et krav som skal prosesseres.
     */
    override fun prosesser(jobbDataString: String) {
        val jobbData = om.readValue<JobbData>(jobbDataString)
        val krav = kroniskKravRepo.getById(jobbData.id)
        requireNotNull(krav, { "Jobben indikerte et krav med id $jobbData men den kunne ikke finnes" })

        try {
            if (krav.journalpostId == null) {
                krav.journalpostId = journalfør(krav)
                KroniskKravMetrics.tellJournalfoert()
            }

            bucketStorage.deleteDoc(krav.id)

            if (krav.oppgaveId == null) {
                krav.oppgaveId = opprettOppgave(krav)
                KroniskKravMetrics.tellOppgaveOpprettet()
            }
            bakgrunnsjobbRepo.save(
                Bakgrunnsjobb(
                    maksAntallForsoek = 10,
                    data = om.writeValueAsString(KroniskKravKafkaProcessor.JobbData(krav.id)),
                    type = KroniskKravKafkaProcessor.JOB_TYPE
                )
            )
        } finally {
            updateAndLogOnFailure(krav)
        }
    }

    private fun updateAndLogOnFailure(krav: KroniskKrav) {
        try {
            kroniskKravRepo.update(krav)
        } catch (e: Exception) {
            throw RuntimeException("Feilet i å lagre ${krav.id} etter at en ekstern operasjon har blitt utført. JournalpostID: ${krav.journalpostId} OppgaveID: ${krav.oppgaveId}", e)
        }
    }

    fun journalfør(krav: KroniskKrav): String {
        val journalfoeringsTittel = "Krav om fritak fra arbeidsgiverperioden ifbm kroniskitet"
        val pdlResponse = pdlClient.personNavn(krav.sendtAv)?.navn?.firstOrNull()
        val innsenderNavn = if (pdlResponse != null) "${pdlResponse.fornavn} ${pdlResponse.etternavn}" else "Ukjent"

        val response = dokarkivKlient.journalførDokument(
            JournalpostRequest(
                tittel = journalfoeringsTittel,
                journalposttype = Journalposttype.INNGAAENDE,
                kanal = "NAV_NO",
                bruker = Bruker(krav.identitetsnummer, IdType.FNR),
                eksternReferanseId = krav.id.toString(),
                avsenderMottaker = AvsenderMottaker(
                    id = krav.sendtAv,
                    idType = IdType.FNR,
                    navn = innsenderNavn
                ),
                dokumenter = createDocuments(krav, journalfoeringsTittel),
                datoMottatt = krav.opprettet.toLocalDate()
            ), true, UUID.randomUUID().toString()

        )

        log.debug("Journalført ${krav.id} med ref ${response.journalpostId}")
        return response.journalpostId
    }

    private fun createDocuments(
        krav: KroniskKrav,
        journalfoeringsTittel: String
    ): List<Dokument> {
        val base64EnkodetPdf = Base64.getEncoder().encodeToString(pdfGenerator.lagPDF(krav))


        val dokumentListe = mutableListOf(
            Dokument(
                dokumentVarianter = listOf(
                    DokumentVariant(
                        fysiskDokument = base64EnkodetPdf
                    )
                ),
                brevkode = "krav_om_fritak_fra_agp_kronisk",
                tittel = journalfoeringsTittel,
            )
        )

        bucketStorage.getDocAsString(krav.id)?.let {
            dokumentListe.add(
                Dokument(
                    dokumentVarianter = listOf(
                        DokumentVariant(
                            fysiskDokument = it.base64Data,
                            filtype = if (it.extension == "jpg") "JPEG" else it.extension.toUpperCase()
                        )
                    ),
                    brevkode = dokumentasjonBrevkode,
                    tittel = "Helsedokumentasjon",
                )
            )
        }

        return dokumentListe
    }

    fun opprettOppgave(krav: KroniskKrav): String {
        val aktoerId = pdlClient.fullPerson(krav.identitetsnummer)?.hentIdenter?.trekkUtIdent(PdlIdent.PdlIdentGruppe.AKTORID)
        requireNotNull(aktoerId, { "Fant ikke AktørID for fnr i ${krav.id}" })

        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = krav.journalpostId,
            beskrivelse = """
                Krav om refusjon av arbeidsgiverperioden ifbm. kroniskitet
            """.trimIndent(),
            tema = "SYK",
            behandlingstype = digitalKravBehandingsType,
            oppgavetype = "BEH_SAK",
            behandlingstema = fritakAGPBehandingsTema,
            aktivDato = LocalDate.now(),
            fristFerdigstillelse = LocalDate.now().plusDays(7),
            prioritet = "NORM"
        )

        return runBlocking { oppgaveKlient.opprettOppgave(request, UUID.randomUUID().toString()).id.toString() }
    }

    data class JobbData(val id: UUID)

}