package no.nav.helse.fritakagp.koin

import com.zaxxer.hikari.HikariDataSource
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.hag.utils.bakgrunnsjobb.PostgresBakgrunnsjobbRepository
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.MetrikkVarsler
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.db.PostgresGravidKravRepository
import no.nav.helse.fritakagp.db.PostgresGravidSoeknadRepository
import no.nav.helse.fritakagp.db.PostgresKroniskKravRepository
import no.nav.helse.fritakagp.db.PostgresKroniskSoeknadRepository
import no.nav.helse.fritakagp.db.createHikariConfig
import no.nav.helse.fritakagp.domain.BeloepBeregning
import no.nav.helse.fritakagp.integration.BrregService
import no.nav.helse.fritakagp.integration.IBrregService
import no.nav.helse.fritakagp.integration.PdlService
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverOppdaterNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessorNy
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonService
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravEndreProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravPDFGenerator
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravSlettProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadPDFGenerator
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravEndreProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravPDFGenerator
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravSlettProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadPDFGenerator
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadProcessor
import no.nav.helse.fritakagp.web.auth.AuthClient
import no.nav.helse.fritakagp.web.auth.IdentityProvider
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.Altinn3Ressurs
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.AltinnMottaker
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.Sendevindu
import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import no.nav.tms.varsel.action.Sensitivitet
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import javax.sql.DataSource
import kotlin.time.Duration.Companion.days

fun prodConfig(env: Env.Prod): Module = module {
    externalSystemClients(env = env)

    single {
        HikariDataSource(
            createHikariConfig(
                jdbcUrl = env.databaseUrl,
                username = env.databaseUsername,
                password = env.databasePassword
            )
        )
    } bind DataSource::class

    single { PostgresGravidSoeknadRepository(ds = get(), om = get()) } bind GravidSoeknadRepository::class
    single { PostgresKroniskSoeknadRepository(ds = get(), om = get()) } bind KroniskSoeknadRepository::class
    single { PostgresGravidKravRepository(ds = get(), om = get()) } bind GravidKravRepository::class
    single { PostgresKroniskKravRepository(ds = get(), om = get()) } bind KroniskKravRepository::class

    single { PostgresBakgrunnsjobbRepository(dataSource = get()) } bind BakgrunnsjobbRepository::class
    single { BakgrunnsjobbService(bakgrunnsjobbRepository = get(), bakgrunnsvarsler = MetrikkVarsler()) }

    single { GravidSoeknadProcessor(gravidSoeknadRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = GravidSoeknadPDFGenerator(), om = get(), bucketStorage = get(), brregService = get()) }
    single { GravidKravProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), brregService = get()) }
    single { GravidKravSlettProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }
    single { GravidKravEndreProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }

    single { KroniskSoeknadProcessor(kroniskSoeknadRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), bakgrunnsjobbRepo = get(), pdlService = get(), pdfGenerator = KroniskSoeknadPDFGenerator(), om = get(), bucketStorage = get(), brregService = get()) }
    single { KroniskKravProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), brregService = get()) }
    single { KroniskKravSlettProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }
    single { KroniskKravEndreProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }

    single { GravidSoeknadKvitteringProcessor(db = get(), om = get(), dialogSender = get()) }

    single { GravidKravKvitteringProcessor(db = get(), om = get(), dialogSender = get()) }

    single { KroniskSoeknadKvitteringProcessor(db = get(), om = get(), dialogSender = get()) }

    single {
        val azureAuthClient: AuthClient = get()
        val altinnMottaker = AltinnMottaker.Altinn3(Altinn3Ressurs.FRITAKAGP)
        ArbeidsgiverNotifikasjonKlient(env.arbeidsgiverNotifikasjonUrl, altinnMottaker, azureAuthClient.fetchToken(IdentityProvider.AZURE_AD, env.scopeArbeidsgivernotifikasjon), Sendevindu.NKS_AAPNINGSTID)
    }
    single { KroniskKravKvitteringProcessor(db = get(), om = get(), dialogSender = get()) }
    single { BrukernotifikasjonService(om = get(), sensitivitetNivaa = Sensitivitet.High, frontendAppBaseUrl = env.frontendUrl) }
    single { BrukernotifikasjonProcessorNy(brukerNotifikasjonProducerFactory = get(), brukernotifikasjonService = get()) }
    single { BrukernotifikasjonProcessor(gravidKravRepo = get(), gravidSoeknadRepo = get(), kroniskKravRepo = get(), kroniskSoeknadRepo = get(), om = get(), brukernotifikasjonSender = get(), sensitivitetNivaa = Sensitivitet.High, frontendAppBaseUrl = env.frontendUrl) }
    single { ArbeidsgiverNotifikasjonProcessor(gravidKravRepo = get(), kroniskKravRepo = get(), om = get(), frontendAppBaseUrl = env.frontendUrl, arbeidsgiverNotifikasjonKlient = get()) }
    single { ArbeidsgiverOppdaterNotifikasjonProcessor(gravidKravRepo = get(), kroniskKravRepo = get(), om = get(), arbeidsgiverNotifikasjonKlient = get()) }
    single { PdlService(pdlClient = get()) }

    single {
        BrregService(
            BrregClient(
                url = env.brregUrl,
                cacheConfig = LocalCache.Config(1.days, 2500)
            )
        )
    } bind IBrregService::class

    single { BeloepBeregning(grunnbeloepClient = get()) }
}
