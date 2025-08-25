package no.nav.helse.fritakagp.web.auth

import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.authorization
import io.ktor.server.routing.RoutingContext
import no.nav.helse.fritakagp.Issuers
import no.nav.helse.fritakagp.auth.AuthClient
import no.nav.helse.fritakagp.auth.fetchOboToken
import no.nav.helse.fritakagp.integration.altinn.ManglerAltinnRettigheterException
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.security.token.support.core.context.TokenValidationContext
import no.nav.security.token.support.core.jwt.JwtToken
import java.time.Instant
import java.util.Date

suspend fun RoutingContext.authorize(authorizer: Altinn3OBOClient, authClient: AuthClient, scope: String, orgnr: String) {
    val fnr = hentIdentitetsnummerFraLoginToken(call.request)
    val userTokenString = getTokenString(call.request)
    val getToken = authClient.fetchOboToken(scope, userTokenString)
    if (!authorizer.harTilgangTilOrganisasjon(fnr, orgnr, getToken)) {
        throw ManglerAltinnRettigheterException()
    }
}

fun hentIdentitetsnummerFraLoginToken(request: ApplicationRequest): String {
    val tokenString = getTokenString(request)
    val pid = JwtToken(tokenString).jwtTokenClaims.get("pid")
    return pid?.toString() ?: JwtToken(tokenString).subject
}

fun hentUtløpsdatoFraLoginToken(request: ApplicationRequest): Date {
    val tokenString = getTokenString(request)
    return JwtToken(tokenString).jwtTokenClaims.expirationTime ?: Date.from(Instant.MIN)
}

fun getTokenString(request: ApplicationRequest): String =
    request.authorization()?.removePrefix("Bearer ")
        ?: throw IllegalAccessException("Du må angi et identitetstoken i Authorization-headeren")

private val pidRegex = Regex("\\d{11}")

fun TokenValidationContext.containsPid(): Boolean =
    getClaims(Issuers.TOKENX)
        .getStringClaim("pid")
        .matches(pidRegex)
