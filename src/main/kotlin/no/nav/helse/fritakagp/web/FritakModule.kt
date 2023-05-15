package no.nav.helse.fritakagp.web

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.authenticate
import io.ktor.config.ApplicationConfig
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.jackson.JacksonConverter
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.routing.IgnoreTrailingSlash
import io.ktor.routing.route
import io.ktor.routing.routing
import no.nav.helse.arbeidsgiver.system.AppEnv
import no.nav.helse.arbeidsgiver.system.getEnvironment
import no.nav.helse.arbeidsgiver.system.getString
import no.nav.helse.fritakagp.web.api.altinnRoutes
import no.nav.helse.fritakagp.web.api.configureExceptionHandling
import no.nav.helse.fritakagp.web.api.gravidRoutes
import no.nav.helse.fritakagp.web.api.kroniskRoutes
import no.nav.helse.fritakagp.web.api.swaggerRoutes
import no.nav.helse.fritakagp.web.api.systemRoutes
import no.nav.security.token.support.ktor.tokenValidationSupport
import org.koin.ktor.ext.get

@KtorExperimentalLocationsAPI
fun Application.fritakModule(config: ApplicationConfig = environment.config) {

    install(IgnoreTrailingSlash)
    install(Authentication) {
        tokenValidationSupport(config = config)
    }

    configureCORSAccess(config)
    configureExceptionHandling()

    install(ContentNegotiation) {
        val commonObjectMapper = get<ObjectMapper>()
        register(ContentType.Application.Json, JacksonConverter(commonObjectMapper))
    }

    routing {
        val apiBasePath = config.getString("ktor.application.basepath")
        route("$apiBasePath/api/v1") {
            authenticate {
                systemRoutes()
                kroniskRoutes(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
                gravidRoutes(get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
                altinnRoutes(get())
            }
        }
        swaggerRoutes("$apiBasePath")
    }
}

private fun Application.configureCORSAccess(config: ApplicationConfig) {
    install(CORS) {
        method(HttpMethod.Options)
        method(HttpMethod.Post)
        method(HttpMethod.Get)
        method(HttpMethod.Delete)
        method(HttpMethod.Patch)

        when (config.getEnvironment()) {
            AppEnv.PROD -> host("arbeidsgiver.nav.no", schemes = listOf("https"))
            AppEnv.PREPROD -> host("arbeidsgiver.intern.dev.nav.no", schemes = listOf("https"))
            else -> anyHost()
        }

        allowCredentials = true
        allowNonSimpleContentTypes = true
    }
}
