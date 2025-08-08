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
import no.nav.helse.fritakagp.integration.altinn.message.Clients
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverOppdaterNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessorNy
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonService
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravAltinnKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravEndreProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravPDFGenerator
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravSlettProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadAltinnKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadPDFGenerator
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravAltinnKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravEndreProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravPDFGenerator
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravSlettProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadAltinnKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadPDFGenerator
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadProcessor
import no.nav.helse.fritakagp.service.PdlService
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

    single { GravidSoeknadProcessor(gravidSoeknadRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = GravidSoeknadPDFGenerator(), om = get(), bucketStorage = get(), brregClient = get()) }
    single { GravidKravProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), brregClient = get()) }
    single { GravidKravSlettProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }
    single { GravidKravEndreProcessor(gravidKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = GravidKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }

    single { KroniskSoeknadProcessor(kroniskSoeknadRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), bakgrunnsjobbRepo = get(), pdlService = get(), pdfGenerator = KroniskSoeknadPDFGenerator(), om = get(), bucketStorage = get(), brregClient = get()) }
    single { KroniskKravProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), bakgrunnsjobbRepo = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), brregClient = get()) }
    single { KroniskKravSlettProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }
    single { KroniskKravEndreProcessor(kroniskKravRepo = get(), dokarkivKlient = get(), oppgaveKlient = get(), pdlService = get(), pdfGenerator = KroniskKravPDFGenerator(), om = get(), bucketStorage = get(), bakgrunnsjobbRepo = get()) }

    single { Clients.iCorrespondenceExternalBasic(env.altinnMeldingUrl) }

    single {
        GravidSoeknadAltinnKvitteringSender(
            altinnTjenesteKode = env.altinnMeldingServiceId,
            iCorrespondenceAgencyExternalBasic = get(),
            username = env.altinnMeldingUsername,
            password = env.altinnMeldingPassword
        )
    } bind GravidSoeknadKvitteringSender::class

    single { GravidSoeknadKvitteringProcessor(gravidSoeknadKvitteringSender = get(), db = get(), om = get()) }

    single {
        GravidKravAltinnKvitteringSender(
            altinnTjenesteKode = env.altinnMeldingServiceId,
            iCorrespondenceAgencyExternalBasic = get(),
            username = env.altinnMeldingUsername,
            password = env.altinnMeldingPassword
        )
    } bind GravidKravKvitteringSender::class

    single { GravidKravKvitteringProcessor(gravidKravKvitteringSender = get(), db = get(), om = get()) }

    single {
        KroniskSoeknadAltinnKvitteringSender(
            altinnTjenesteKode = env.altinnMeldingServiceId,
            iCorrespondenceAgencyExternalBasic = get(),
            username = env.altinnMeldingUsername,
            password = env.altinnMeldingPassword
        )
    } bind KroniskSoeknadKvitteringSender::class
    single { KroniskSoeknadKvitteringProcessor(kroniskSoeknadKvitteringSender = get(), db = get(), om = get()) }

    single {
        KroniskKravAltinnKvitteringSender(
            altinnTjenesteKode = env.altinnMeldingServiceId,
            iCorrespondenceAgencyExternalBasic = get(),
            username = env.altinnMeldingUsername,
            password = env.altinnMeldingPassword
        )
    } bind KroniskKravKvitteringSender::class
    single { KroniskKravKvitteringProcessor(kroniskKravKvitteringSender = get(), db = get(), om = get()) }
    single { BrukernotifikasjonService(om = get(), sensitivitetNivaa = Sensitivitet.High, frontendAppBaseUrl = env.frontendUrl) }
    single { BrukernotifikasjonProcessorNy(brukerNotifikasjonProducerFactory = get(), brukernotifikasjonService = get()) }
    single { BrukernotifikasjonProcessor(gravidKravRepo = get(), gravidSoeknadRepo = get(), kroniskKravRepo = get(), kroniskSoeknadRepo = get(), om = get(), brukernotifikasjonSender = get(), sensitivitetNivaa = Sensitivitet.High, frontendAppBaseUrl = env.frontendUrl) }
    single { ArbeidsgiverNotifikasjonProcessor(gravidKravRepo = get(), kroniskKravRepo = get(), om = get(), frontendAppBaseUrl = env.frontendUrl, arbeidsgiverNotifikasjonKlient = get()) }
    single { ArbeidsgiverOppdaterNotifikasjonProcessor(gravidKravRepo = get(), kroniskKravRepo = get(), om = get(), arbeidsgiverNotifikasjonKlient = get()) }
    single { PdlService(pdlClient = get()) }

    single {
        BrregClient(
            url = env.brregUrl,
            cacheConfig = LocalCache.Config(1.days, 2500)
        )
    }

    single { BeloepBeregning(grunnbeloepClient = get()) }
}
