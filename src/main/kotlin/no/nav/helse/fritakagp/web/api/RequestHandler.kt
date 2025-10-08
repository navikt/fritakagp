package no.nav.helse.fritakagp.web.api

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.server.request.receiveText
import io.ktor.server.routing.RoutingContext
import no.nav.helse.fritakagp.customObjectMapper
import org.slf4j.Logger
import java.util.UUID

class RequestHandler(
    val aapenLogger: Logger,
    val sikkerLogger: Logger
) {
    val objectMapper = customObjectMapper()

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
        val body = context.call.receiveText()
        sikkerLogger.info("Mottatt request body: $body")

        return try {
            objectMapper.readValue<T>(body)
        } catch (e: Exception) {
            val feilmelding = "Klarte ikke lese request body."
            aapenLogger.error(feilmelding)
            sikkerLogger.error("$feilmelding\n'requestBody'=$body", e)
            throw e
        }
    }
}
