package no.nav.helse.fritakagp.web.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.helse.fritakagp.web.auth.hentUtløpsdatoFraLoginToken

fun Route.systemRoutes() {
    get("/login-expiry") {
        call.respond(HttpStatusCode.OK, hentUtløpsdatoFraLoginToken(call.request))
    }
}
