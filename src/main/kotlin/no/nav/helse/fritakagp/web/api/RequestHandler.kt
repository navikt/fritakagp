package no.nav.helse.fritakagp.web.api

import io.ktor.server.request.receive
import io.ktor.server.request.receiveText
import io.ktor.server.routing.RoutingContext
import org.slf4j.Logger
import java.util.UUID

class RequestHandler(
    val aapenLogger: Logger,
    val sikkerLogger: Logger
) {
    fun lesParameterId(context: RoutingContext): UUID {
        val id = context.call.parameters["id"]

        return try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            val feilmelding = "Klarte ikke parse parameter 'id' til UUID."
            aapenLogger.error(feilmelding)
            sikkerLogger.error("$feilmelding 'id'='$id'", e)
            throw e
        }
    }

    suspend inline fun <reified T : Any> lesRequestBody(context: RoutingContext): T {
        return try {
            context.call.receive<T>()
        } catch (e: Exception) {
            val feilmelding = "Klarte ikke lese request."
            aapenLogger.error(feilmelding)
            sikkerLogger.error("$feilmelding\n'request'=${context.call.receiveText()}", e)
            throw e
        }
    }
}
