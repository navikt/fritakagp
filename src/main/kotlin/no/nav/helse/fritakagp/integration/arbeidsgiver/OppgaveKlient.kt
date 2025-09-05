package no.nav.helse.fritakagp.integration.arbeidsgiver

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.withCharset
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.utils.log.logger
import java.time.LocalDate

interface OppgaveKlient {
    suspend fun opprettOppgave(opprettOppgaveRequest: OpprettOppgaveRequest, callId: String): OpprettOppgaveResponse
    suspend fun hentOppgave(oppgaveId: Int, callId: String): OppgaveResponse
}

interface SyncOppgaveKlient {
    fun opprettOppgaveSync(opprettOppgaveRequest: OpprettOppgaveRequest, callId: String): OpprettOppgaveResponse
    fun hentOppgaveSync(oppgaveId: Int, callId: String): OppgaveResponse
}

class OppgaveKlientImpl(
    private val url: String,
    val getAccessToken: () -> String,
    private val httpClient: HttpClient
) : OppgaveKlient, SyncOppgaveKlient {

    override suspend fun opprettOppgave(opprettOppgaveRequest: OpprettOppgaveRequest, callId: String): OpprettOppgaveResponse {
        logger().info("Oppretter oppgave for journalpost ${opprettOppgaveRequest.journalpostId} med saksreferanse ${opprettOppgaveRequest.saksreferanse} og X-Correlation-ID $callId")
        val token = getAccessToken()
        val httpResponse = httpClient.post(url) {
            contentType(ContentType.Application.Json.withCharset(Charsets.UTF_8))
            header("Authorization", "Bearer $token")
            header("X-Correlation-ID", callId)
            setBody(opprettOppgaveRequest)
        }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.call.response.body()
            }
            HttpStatusCode.Created -> {
                httpResponse.call.response.body()
            }
            else -> {
                logger().error("Feilet å opprette oppgave : $httpResponse")
                throw OpprettOppgaveUnauthorizedException(opprettOppgaveRequest, httpResponse.status)
            }
        }
    }

    override fun opprettOppgaveSync(
        opprettOppgaveRequest: OpprettOppgaveRequest,
        callId: String
    ): OpprettOppgaveResponse {
        return runBlocking { opprettOppgave(opprettOppgaveRequest, callId) }
    }

    override suspend fun hentOppgave(oppgaveId: Int, callId: String): OppgaveResponse {
        val token = getAccessToken()
        val httpResponse = httpClient.get("$url/$oppgaveId") {
            contentType(ContentType.Application.Json)
            header("Authorization", "Bearer $token")
            header("X-Correlation-ID", callId)
        }
        return when (httpResponse.status) {
            HttpStatusCode.OK -> {
                httpResponse.call.response.body()
            }
            else -> {
                throw HentOppgaveUnauthorizedException(oppgaveId, httpResponse.status)
            }
        }
    }

    override fun hentOppgaveSync(
        oppgaveId: Int,
        callId: String
    ): OppgaveResponse {
        return runBlocking { hentOppgave(oppgaveId, callId) }
    }
}

data class OppgaveResponse(
    val id: Int? = null,
    val versjon: Int? = null,
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val journalpostId: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val saksreferanse: String? = null,
    val tilordnetRessurs: String? = null,
    val beskrivelse: String? = null,
    val tema: String? = null,
    val oppgavetype: String,
    val behandlingstype: String? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String,
    val status: String? = null,
    val mappeId: Int? = null
)

data class OpprettOppgaveRequest(
    val tildeltEnhetsnr: String? = null,
    val opprettetAvEnhetsnr: String? = null,
    val aktoerId: String? = null,
    val orgnr: String? = null,
    val journalpostId: String? = null,
    val journalpostkilde: String? = null,
    val behandlesAvApplikasjon: String? = null,
    val tilordnetRessurs: String? = null,

    val saksreferanse: String? = null,
    val beskrivelse: String? = null,
    val temagruppe: String? = null,
    val tema: String,
    val oppgavetype: String,

    /**
     * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Behandlingstyper
     */
    val behandlingstype: String? = null,

    /**
     * https://kodeverk-web.nais.adeo.no/kodeverksoversikt/kodeverk/Behandlingstema
     */
    val behandlingstema: String? = null,
    val aktivDato: LocalDate,
    val fristFerdigstillelse: LocalDate? = null,
    val prioritet: String
)

// https://oppgave.dev.adeo.no/#/Oppgave/opprettOppgave
data class OpprettOppgaveResponse(
    val id: Int,
    val tildeltEnhetsnr: String,
    val tema: String,
    val oppgavetype: String,
    val versjon: Int,
    val aktivDato: LocalDate,
    val prioritet: Prioritet,
    val status: Status
)

enum class Status { OPPRETTET, AAPNET, UNDER_BEHANDLING, FERDIGSTILT, FEILREGISTRERT }
enum class Prioritet { HOY, NORM, LAV }

const val OPPGAVETYPE_FORDELINGSOPPGAVE = "FDR"
