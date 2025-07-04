package no.nav.helse.fritakagp.web.api

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ParameterConversionException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import no.nav.helse.fritakagp.integration.altinn.ManglerAltinnRettigheterException
import no.nav.helse.fritakagp.web.api.resreq.validation.Problem
import no.nav.helse.fritakagp.web.api.resreq.validation.ValidationProblem
import no.nav.helse.fritakagp.web.api.resreq.validation.ValidationProblemDetail
import no.nav.helse.fritakagp.web.api.resreq.validation.getContextualMessage
import no.nav.helsearbeidsgiver.utils.log.logger
import org.valiktor.ConstraintViolationException
import java.lang.reflect.InvocationTargetException
import java.net.URI
import java.util.UUID

fun Application.configureExceptionHandling() {
    install(StatusPages) {
        val logger = "StatusPages".logger()

        suspend fun handleUnexpectedException(call: ApplicationCall, cause: Throwable) {
            val errorId = UUID.randomUUID()

            val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: "Ukjent"
            logger.error("Uventet feil, $errorId med useragent $userAgent", cause)
            val problem = Problem(
                type = URI.create("urn:fritak:uventet-feil"),
                title = "Uventet feil",
                detail = cause.message,
                instance = URI.create("urn:fritak:uventent-feil:$errorId")
            )
            call.respond(HttpStatusCode.InternalServerError, problem)
        }

        suspend fun handleValidationError(call: ApplicationCall, cause: ConstraintViolationException) {
            val problems = cause.constraintViolations.map {
                ValidationProblemDetail(it.constraint.name, it.getContextualMessage(), it.property, it.value)
            }.toSet()

            call.respond(HttpStatusCode.UnprocessableEntity, ValidationProblem(problems))
        }

        exception<InvocationTargetException> { call, cause ->
            when (cause.targetException) {
                is ConstraintViolationException -> handleValidationError(
                    call,
                    cause.targetException as ConstraintViolationException
                )
                else -> handleUnexpectedException(call, cause)
            }
        }

        exception<ManglerAltinnRettigheterException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                Problem(URI.create("urn:fritak:forbidden"), "Ikke korrekte Altinnrettigheter på valgt virksomhet", HttpStatusCode.Forbidden.value)
            )
        }

        exception<Throwable> { call, cause ->
            handleUnexpectedException(call, cause)
        }

        exception<ParameterConversionException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ValidationProblem(
                    setOf(
                        ValidationProblemDetail(
                            "ParameterConversion",
                            "Parameteret kunne ikke  konverteres til ${cause.type}",
                            cause.parameterName,
                            null
                        )
                    )
                )
            )
            logger.warn("${cause.parameterName} kunne ikke konverteres")
        }

        exception<BadRequestException> { call, cause ->
            val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: "Ukjent"
            call.respond(
                HttpStatusCode.BadRequest,
                ValidationProblem(
                    setOf(
                        ValidationProblemDetail(
                            "BadInput",
                            "Feil input",
                            "",
                            "null"
                        )
                    )
                )
            )
            logger.warn("Feil med validering av ${call.request.rawQueryParameters} for $userAgent: ${cause.message}")
        }

        exception<MissingKotlinParameterException> { call, cause ->
            val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: "Ukjent"
            call.respond(
                HttpStatusCode.BadRequest,
                ValidationProblem(
                    setOf(
                        ValidationProblemDetail(
                            "NotNull",
                            "Det angitte feltet er påkrevd",
                            cause.path.filter { it.fieldName != null }.joinToString(".") {
                                it.fieldName
                            },
                            "null"
                        )
                    )
                )
            )
            logger.warn("Feil med validering av ${cause.parameter.name ?: "Ukjent"} for $userAgent: ${cause.message}")
        }

        exception<JsonMappingException> { call, cause ->
            if (cause.cause is ConstraintViolationException) {
                handleValidationError(call, cause.cause as ConstraintViolationException)
            } else {
                val errorId = UUID.randomUUID()
                val userAgent = call.request.headers[HttpHeaders.UserAgent] ?: "Ukjent"
                val locale = call.request.headers[HttpHeaders.AcceptLanguage] ?: "Ukjent"
                logger.warn("$errorId : $userAgent : $locale", cause)
                val problem = Problem(
                    status = HttpStatusCode.BadRequest.value,
                    title = "Feil ved prosessering av JSON-dataene som ble oppgitt",
                    detail = cause.message,
                    instance = URI.create("urn:fritak:json-mapping-error:$errorId")
                )
                call.respond(HttpStatusCode.BadRequest, problem)
            }
        }

        exception<ConstraintViolationException> { call, cause ->
            handleValidationError(call, cause)
        }
    }
}
