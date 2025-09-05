package no.nav.helse.fritakagp.web.auth

import io.ktor.server.request.authorization
import io.ktor.server.routing.RoutingContext
import no.nav.helse.fritakagp.Issuers
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.jwt.JwtToken
import java.time.Instant
import java.util.Date

private const val PID_NAME = "pid"
private val pidRegex = Regex("\\d{11}")

fun RoutingContext.hentFnrFraLoginToken(): String =
    getTokenString()
        .let(::JwtToken)
        .let {
            it.jwtTokenClaims.get(PID_NAME)?.toString()
                ?: it.subject
        }

fun RoutingContext.hentUtloepsdatoFraLoginToken(): Date =
    getTokenString()
        .let(::JwtToken)
        .jwtTokenClaims
        .expirationTime
        ?: Date.from(Instant.MIN)

fun RoutingContext.getTokenString(): String =
    call.request.authorization()?.removePrefix("Bearer ")
        ?: throw IllegalAccessException("Du må angi et identitetstoken i Authorization-headeren")

fun TokenValidationContext.containsPid(): Boolean =
    getClaims(Issuers.TOKENX)
        .getStringClaim(PID_NAME)
        .matches(pidRegex)
