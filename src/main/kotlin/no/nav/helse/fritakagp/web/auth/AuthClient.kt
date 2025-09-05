package no.nav.helse.fritakagp.web.auth

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.forms.submitForm
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.parameters
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.serialization.jackson.jackson
import kotlinx.coroutines.runBlocking
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.log.sikkerLogger

enum class IdentityProvider(@JsonValue val alias: String) {
    MASKINPORTEN("maskinporten"),
    AZURE_AD("azuread"),
    TOKEN_X("tokenx")
}

sealed class TokenResponse {
    data class Success(
        @JsonProperty("access_token")
        val accessToken: String,
        @JsonProperty("expires_in")
        val expiresInSeconds: Int
    ) : TokenResponse()

    data class Error(
        val error: TokenErrorResponse,
        val status: HttpStatusCode
    ) : TokenResponse()
}

data class TokenErrorResponse(
    val error: String,
    @JsonProperty("error_description")
    val errorDescription: String
)

class AuthClient(
    private val tokenEndpoint: String,
    private val tokenExchangeEndpoint: String
) {
    private val httpClient = createHttpClient()
    suspend fun token(provider: IdentityProvider, target: String): TokenResponse = try {
        logger().debug("Henter token fra ${provider.alias}")
        httpClient.submitForm(
            tokenEndpoint,
            parameters {
                set("target", target)
                set("identity_provider", provider.alias)
            }
        ).body<TokenResponse.Success>()
    } catch (e: ResponseException) {
        TokenResponse.Error(e.response.body<TokenErrorResponse>(), e.response.status)
    }

    suspend fun exchange(provider: IdentityProvider, target: String, userToken: String): TokenResponse = try {
        httpClient.submitForm(
            tokenExchangeEndpoint,
            parameters {
                set("target", target)
                set("user_token", userToken)
                set("identity_provider", provider.alias)
            }
        ).body<TokenResponse.Success>()
    } catch (e: ResponseException) {
        TokenResponse.Error(e.response.body<TokenErrorResponse>(), e.response.status)
    }

fun AuthClient.fetchToken(identityProvider: IdentityProvider, target: String): () -> String = {
    runBlocking {
        token(identityProvider, target).let {
            when (it) {
                is TokenResponse.Success -> it.accessToken
                is TokenResponse.Error -> {
                    logger().error("Feilet å hente token")
                    sikkerLogger().error("Feilet å hente token status: ${it.status} - ${it.error.errorDescription}")
                    throw RuntimeException("Feilet å hente token status: ${it.status} - ${it.error.errorDescription}")
                }
            }
        }
    }
}

    fun fetchOboToken(
        target: String,
        userToken: String
    ): () -> String =
        {
            runBlocking {
                exchange(IdentityProvider.TOKEN_X, target, userToken).let {
                    when (it) {
                        is TokenResponse.Success -> it.accessToken
                        is TokenResponse.Error -> {
                            sikkerLogger().error("Feilet å hente obo token status: ${it.status} - ${it.error.errorDescription}")
                            throw RuntimeException("Feilet å hente obo token status: ${it.status} - ${it.error.errorDescription}")
                        }
                    }
                }
            }
        }
}

fun createHttpClient(): HttpClient = HttpClient(Apache5) {
    expectSuccess = true
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(customObjectMapper()))
        jackson {
            registerModule(JavaTimeModule())
        }
    }
}
