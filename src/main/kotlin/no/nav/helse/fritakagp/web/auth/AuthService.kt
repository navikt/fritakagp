package no.nav.helse.fritakagp.web.auth

import io.ktor.server.routing.RoutingContext
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.helsearbeidsgiver.altinn.AltinnTilgang

class AuthService(
    private val authClient: AuthClient,
    private val altinnClient: Altinn3OBOClient,
    private val altinnTilgangerScope: String
) {
    suspend fun validerTilgangTilOrganisasjon(context: RoutingContext, orgnr: String) {
        val innloggetFnr = context.hentFnrFraLoginToken()

        val harTilgang = altinnClient.harTilgangTilOrganisasjon(
            fnr = innloggetFnr,
            orgnr = orgnr,
            getToken = context.getTokenFn()
        )

        if (!harTilgang) {
            throw ManglerAltinnRettigheterException()
        }
    }

    suspend fun hentHierarkiMedTilganger(context: RoutingContext): List<AltinnTilgang> {
        val innloggetFnr = context.hentFnrFraLoginToken()
        val hierarkiMedTilganger = altinnClient.hentHierarkiMedTilganger(innloggetFnr, context.getTokenFn())
        return hierarkiMedTilganger.hierarki
    }

    private fun RoutingContext.getTokenFn(): () -> String {
        val userTokenString = getTokenString()
        return authClient.fetchOboToken(
            target = altinnTilgangerScope,
            userToken = userTokenString
        )
    }
}

class ManglerAltinnRettigheterException : Exception()
