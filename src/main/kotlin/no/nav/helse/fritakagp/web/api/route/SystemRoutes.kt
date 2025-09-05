package no.nav.helse.fritakagp.web.api.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.helse.fritakagp.web.auth.hentUtloepsdatoFraLoginToken

fun Route.systemRoutes() {
    get("/login-expiry") {
        call.respond(HttpStatusCode.OK, hentUtloepsdatoFraLoginToken())
    }
}
