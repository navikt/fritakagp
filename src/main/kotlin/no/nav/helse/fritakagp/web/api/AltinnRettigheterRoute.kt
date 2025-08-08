package no.nav.helse.fritakagp.web.api

import io.ktor.client.plugins.ServerResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.helse.fritakagp.auth.AuthClient
import no.nav.helse.fritakagp.auth.fetchOboToken
import no.nav.helse.fritakagp.web.auth.getTokenString
import no.nav.helse.fritakagp.web.auth.hentIdentitetsnummerFraLoginToken
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

fun Route.altinnRoutes(
    altinn3OBOClient: Altinn3OBOClient,
    authClient: AuthClient,
    fagerScope: String
) {
    val sikkerlogger = sikkerLogger()
    get("/arbeidsgiver-tilganger") {
        val id = hentIdentitetsnummerFraLoginToken(call.request)
        sikkerlogger.info("Henter arbeidsgivertilganger for ${id.take(6)}")
        try {
            val hierarkiMedTilganger = altinn3OBOClient.hentHierarkiMedTilganger(id, authClient.fetchOboToken(fagerScope, getTokenString(call.request)))
            sikkerlogger.info("Hentet arbeidsgivertilganger for ${id.take(6)} med ${hierarkiMedTilganger.hierarki.size} arbeidsgivere.")
            call.respond(hierarkiMedTilganger.hierarki)
        } catch (e: ServerResponseException) {
            sikkerlogger.warn("Fikk en feilmelding fra altinn-tilganger api", e)
            call.respond(HttpStatusCode.ExpectationFailed, "Uventet feil prøv igjen om litt")
        }
    }
}
