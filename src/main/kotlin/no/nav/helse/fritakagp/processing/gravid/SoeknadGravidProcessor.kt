package no.nav.helse.fritakagp.processing.gravid

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.helse.arbeidsgiver.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.arbeidsgiver.integrasjoner.dokarkiv.*
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave.OpprettOppgaveRequest
import no.nav.helse.arbeidsgiver.integrasjoner.pdl.PdlClient
import no.nav.helse.arbeidsgiver.integrasjoner.pdl.PdlIdent
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.domain.SoeknadGravid
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SoeknadGravidProcessor(
        private val gravidSoeknadRepo: GravidSoeknadRepository,
        private val dokarkivKlient: DokarkivKlient,
        private val oppgaveKlient: OppgaveKlient,
        private val pdlClient: PdlClient,
        private val pdfGenerator: GravidSoeknadPDFGenerator,
        private val om: ObjectMapper
) : BakgrunnsjobbProsesserer {
    companion object { val JOB_TYPE = "PROC_GRAVID" }

    val digitalSoeknadBehandingsType = "ae0227"
    val fritakAGPBehandingsTema = "ab0338"

    val log = LoggerFactory.getLogger(SoeknadGravidProcessor::class.java)

    override fun nesteForsoek(forsoek: Int, forrigeForsoek: LocalDateTime): LocalDateTime {
        return LocalDateTime.now().plusHours(3)
    }

    /**
     * Prosesserer en gravidsøknad; journalfører søknaden og oppretter en oppgave for saksbehandler.
     * Jobbdataene forventes å være en UUID for en søknad som skal prosesseres.
     */
    override fun prosesser(jobbDataString: String) {
        val jobbData = om.readValue<JobbData>(jobbDataString)
        val soeknad = gravidSoeknadRepo.getById(jobbData.id)
        requireNotNull(soeknad, {"Jobben indikerte en søknad med id $jobbData men den kunne ikke finnes"})

        try {
            if (soeknad.journalpostId == null) {
                soeknad.journalpostId = journalfør(soeknad)
            }

            if (soeknad.oppgaveId == null) {
                soeknad.oppgaveId = opprettOppgave(soeknad)
            }
        } finally {
            updateAndLogOnFailure(soeknad)
        }
    }

    private fun updateAndLogOnFailure(soeknad: SoeknadGravid) {
        try {
            gravidSoeknadRepo.update(soeknad)
        } catch (e: Exception) {
            log.error("Feilet i å lagre ${soeknad.id} etter at en ekstern operasjon har blitt utført. JournalpostID: ${soeknad.journalpostId} OppgaveID: ${soeknad.oppgaveId}")
            throw e
        }
    }

    fun journalfør(soeknad: SoeknadGravid): String {
        val journalfoeringsTittel = "Søknad om fritak fra arbeidsgiverperioden ifbm sykedom"
        val base64EnkodetPdf = Base64.getEncoder().encodeToString(pdfGenerator.lagPDF(soeknad))
        val pdlResponse = pdlClient.personNavn(soeknad.sendtAv)?.navn?.firstOrNull()
        val innsenderNavn = if (pdlResponse != null) "${pdlResponse.fornavn} ${pdlResponse.etternavn}" else "Ukjent"

        val response = dokarkivKlient.journalførDokument(
            JournalpostRequest(
                tittel = journalfoeringsTittel,
                journalposttype = Journalposttype.INNGAAENDE,
                kanal = "NAV_NO",
                bruker = Bruker(soeknad.fnr, IdType.FNR),
                eksternReferanseId = soeknad.id.toString(),
                avsenderMottaker = AvsenderMottaker(
                    id = soeknad.sendtAv,
                    idType = IdType.FNR,
                    navn = innsenderNavn
                ),
                dokumenter = listOf(
                    Dokument(
                    dokumentVarianter = listOf(
                        DokumentVariant(
                        fysiskDokument = base64EnkodetPdf
                    )
                    ),
                    brevkode = "soeknad_om_fritak_fra_agp",
                    tittel = journalfoeringsTittel,
                )
                ),
                datoMottatt = soeknad.opprettet.toLocalDate()
            ), true, UUID.randomUUID().toString()

        )

        log.debug("Journalført ${soeknad.id} med ref ${response.journalpostId}")
        return response.journalpostId
    }

    fun opprettOppgave(soeknad: SoeknadGravid): String {
        val aktoerId = pdlClient.fullPerson(soeknad.fnr)?.hentIdenter?.trekkUtIdent(PdlIdent.PdlIdentGruppe.AKTORID)
        requireNotNull(aktoerId, { "Fant ikke AktørID for fnr i ${soeknad.id}" })

        val request = OpprettOppgaveRequest(
            aktoerId = aktoerId,
            journalpostId = soeknad.journalpostId,
            beskrivelse = """
                Søknad om fritak fra arbeidsgiverperioden.w
            """.trimIndent(),
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

    data class JobbData(val id: UUID)

}