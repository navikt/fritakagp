package no.nav.helse.fritakagp

import io.ktor.server.config.ApplicationConfig
import no.nav.security.token.support.v3.IssuerConfig
import no.nav.security.token.support.v3.TokenSupportConfig

object Issuers {
    const val TOKENX = "tokenx-issuer"
}

fun readEnv(config: ApplicationConfig): Env =
    when (config.prop("koin.profile")) {
        "PROD" -> Env::Prod
        "PREPROD" -> Env::Preprod
        else -> Env::Local
    }
        .invoke(config)

sealed class Env(
    private val config: ApplicationConfig
) {
    class Prod(config: ApplicationConfig) : Env(config)
    class Preprod(config: ApplicationConfig) : Env(config)
    class Local(config: ApplicationConfig) : Env(config)

    val ktorBasepath = "ktor.application.basepath".prop()

    val frontendUrl = "frontend_app_url".prop()

    val databaseUrl = "jdbc:postgresql://%s:%s/%s".format("database.host".prop(), "database.port".prop(), "database.name".prop())
    val databaseUsername = "database.username".prop()
    val databasePassword = "database.password".prop()

    val gcpBucketName = "gcp_bucket_name".prop()
    val gcpProjectId = "gcp_prjId".prop()

    val kafkaTopicNameBrukernotifikasjon = "brukernotifikasjon.topic_name".prop()

    // Integrasjoner (URL)

    val altinnMeldingUrl = "altinn_melding.altinn_endpoint".prop()
    val altinnMeldingServiceId = "altinn_melding.service_id".prop()
    val altinnMeldingUsername = "altinn_melding.username".prop()
    val altinnMeldingPassword = "altinn_melding.password".prop()

    val altinnServiceOwnerServiceId = "altinn.service_id".prop()

    val altinnTilgangerScope = "altinn_tilganger.scope".prop()
    val altinnTilgangerBaseUrl = "altinn_tilganger.base_url".prop()

    val tokenEndpoint = "auth.token_endpoint".prop()
    val tokenExchangeEndpoint = "auth.token_exchange_endpoint".prop()
    val tokenIntrospectionEndpoint = "auth.token_introspection_endpoint".prop()

    val tokenxDiscoveryUrl = "tokenx_config.discoveryurl".prop()
    val tokenxAcceptedAudience = "tokenx_config.accepted_audience".prop()

    val tokenxConfig =
        TokenSupportConfig(
            IssuerConfig(
                name = Issuers.TOKENX,
                discoveryUrl = tokenxDiscoveryUrl,
                acceptedAudience = tokenxAcceptedAudience.let(::listOf)
            )
        )

    val aaregUrl = "aareg_url".prop()

    val arbeidsgiverNotifikasjonUrl = "arbeidsgiver_notifikasjon_api_url".prop()

    val brregUrl = "brreg_enhet_url".prop()

    val clamAvUrl = "clamav_url".prop()

    val dokarkivUrl = "dokarkiv.base_url".prop()

    val grunnbeloepUrl = "grunnbeloep_url".prop()

    val oppgavebehandlingUrl = "oppgavebehandling.url".prop()

    val pdlUrl = "pdl_url".prop()

    val scopeOppgave = "oppgavescope".prop()
    val scopeDokarkiv = "dokarkivscope".prop()
    val scopeArbeidsgivernotifikasjon = "arbeidsgivernotifikasjonscope".prop()
    val scopePdl = "pdlscope".prop()
    val scopeAareg = "aaregscope".prop()

    private fun String.prop(): String =
        config.prop(this)
}

private fun ApplicationConfig.prop(key: String): String =
    property(key).getString()
