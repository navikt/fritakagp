package no.nav.helse.fritakagp.integration.arbeidsgiver

import io.ktor.http.HttpStatusCode

class HentOppgaveUnauthorizedException(oppgaveId: Int, status: HttpStatusCode) :
    RuntimeException("Klarte ikke hente oppgave $oppgaveId - fikk status $status")

class OpprettOppgaveUnauthorizedException(opprettOppgaveRequest: OpprettOppgaveRequest, status: HttpStatusCode) :
    RuntimeException(
        "Klarte ikke oprette oppgave for journalpost ${opprettOppgaveRequest.journalpostId} " +
            "med saksreferanse ${opprettOppgaveRequest.saksreferanse} - fikk status $status"
    )
