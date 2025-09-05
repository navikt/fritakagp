package no.nav.helse.fritakagp.web.api.route

import io.ktor.server.http.content.staticResources
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

fun Route.swaggerRoutes(base: String) {
    route(base) {
        staticResources("swagger", "swagger-ui/dist")
        staticResources("docs", "swagger-docs")
    }
}
