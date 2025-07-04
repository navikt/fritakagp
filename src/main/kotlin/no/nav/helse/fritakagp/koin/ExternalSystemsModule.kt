package no.nav.helse.fritakagp.koin

import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlientImpl
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.auth.AuthClient
import no.nav.helse.fritakagp.auth.IdentityProvider
import no.nav.helse.fritakagp.auth.fetchToken
import no.nav.helse.fritakagp.integration.GrunnbeloepClient
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.gcp.BucketStorageImpl
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonKafkaProducer
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonSender
import no.nav.helse.fritakagp.integration.kafka.brukernotifikasjonKafkaProps
import no.nav.helse.fritakagp.integration.virusscan.ClamavVirusScannerImp
import no.nav.helse.fritakagp.integration.virusscan.VirusScanner
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.pdl.Behandlingsgrunnlag
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import org.koin.core.module.Module
import org.koin.dsl.bind
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

fun Module.externalSystemClients(env: Env) {
    single {
        Altinn3OBOClient(
            baseUrl = env.altinnTilgangerBaseUrl,
            serviceCode = env.altinnServiceOwnerServiceId,
            cacheConfig = LocalCache.Config(60.minutes, 250)
        )
    } bind Altinn3OBOClient::class

    single { GrunnbeloepClient(env.grunnbeloepUrl, get()) }

    single {
        val azureAuthClient: AuthClient = get()
        PdlClient(
            url = env.pdlUrl,
            behandlingsgrunnlag = Behandlingsgrunnlag.FRITAKAGP,
            cacheConfig = LocalCache.Config(1.days, 10_000),
            getAccessToken = azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, env.scopePdl)
        )
    } bind PdlClient::class

    single {
        val azureAuthClient: AuthClient = get()
        AaregClient(
            baseUrl = env.aaregUrl,
            cacheConfig = LocalCache.Config(5.minutes, 500),
            getAccessToken = azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, env.scopeAareg)
        )
    } bind AaregClient::class

    single {
        val azureAuthClient: AuthClient = get()
        DokArkivClient(env.dokarkivUrl, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, env.scopeDokarkiv))
    }

    single {
        val azureAuthClient: AuthClient = get()
        OppgaveKlientImpl(env.oppgavebehandlingUrl, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, env.scopeOppgave), get())
    } bind OppgaveKlient::class

    single {
        val azureAuthClient: AuthClient = get()
        ArbeidsgiverNotifikasjonKlient(env.arbeidsgiverNotifikasjonUrl, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, env.scopeArbeidsgivernotifikasjon))
    }

    single {
        ClamavVirusScannerImp(
            get(),
            env.clamAvUrl
        )
    } bind VirusScanner::class

    single {
        BucketStorageImpl(
            env.gcpBucketName,
            env.gcpProjectId
        )
    } bind BucketStorage::class

    single {
        BrukernotifikasjonKafkaProducer(
            brukernotifikasjonKafkaProps(),
            env.kafkaTopicNameBrukernotifikasjon
        )
    } bind BrukernotifikasjonSender::class

    single { AuthClient(tokenEndpoint = env.tokenEndpoint, tokenExchangeEndpoint = env.tokenExchangeEndpoint, tokenIntrospectionEndpoint = env.tokenIntrospectionEndpoint) }
}
