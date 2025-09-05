package no.nav.helse.fritakagp.web.api.route

import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.helse.fritakagp.web.auth.AuthService
import no.nav.helse.fritakagp.web.auth.hentFnrFraLoginToken
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

fun Route.altinnRoutes(
    authService: AuthService
) {
    val sikkerlogger = sikkerLogger()
    get("/arbeidsgiver-tilganger") {
        val innloggetFoedselsdato = hentFnrFraLoginToken().take(6)
        sikkerlogger.info("Henter arbeidsgivertilganger for $innloggetFoedselsdato")
        try {
            val hierarkiMedTilganger = authService.hentHierarkiMedTilganger(this)
            sikkerlogger.info("Hentet arbeidsgivertilganger for $innloggetFoedselsdato med ${hierarkiMedTilganger.size} arbeidsgivere.")
            call.respond(hierarkiMedTilganger)
        } catch (e: ServerResponseException) {
            sikkerlogger.warn("Fikk en feilmelding fra altinn-tilganger API.", e)
            call.respond(HttpStatusCode.ExpectationFailed, "Uventet feil prøv igjen om litt")
        }
    }
}
