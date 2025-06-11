package no.nav.helse.fritakagp.koin

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.serialization.jackson.jackson
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.customObjectMapper
import org.koin.core.module.Module
import org.koin.dsl.module

fun profileModules(env: Env): List<Module> {
    val envModule = when (env) {
        is Env.Prod -> prodConfig(env)
        is Env.Preprod -> preprodConfig(env)
        is Env.Local -> localConfig(env)
    }

    return listOf(common, envModule)
}

private val common = module {
    single { customObjectMapper() }

    single {
        HttpClient(Apache) {
            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(customObjectMapper()))
                jackson {
                    registerModule(JavaTimeModule())
                }
            }
        }
    }
}
