package no.nav.helse.fritakagp

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helse.fritakagp.koin.profileModules
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverOppdaterNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessorNy
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravEndreProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravSlettProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravEndreProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravSlettProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadProcessor
import no.nav.helse.fritakagp.web.auth.localAuthTokenDispenser
import no.nav.helse.fritakagp.web.fritakModule
import no.nav.helse.fritakagp.web.nais.nais
import org.flywaydb.core.Flyway
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.slf4j.LoggerFactory

class FritakAgpApplication(val port: Int = 8080, val runAsDeamon: Boolean = true) : KoinComponent {
    private val logger = LoggerFactory.getLogger(this.javaClass)
    private val appConfig = HoconApplicationConfig(ConfigFactory.load())
    private val env = readEnv(appConfig)

    private val webserver: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>

    init {
        if (env is Env.Preprod || env is Env.Prod) {
            logger.info("Sover i 30s i påvente av SQL proxy sidecar")
            Thread.sleep(30000)
        }

        startKoin { modules(profileModules(env)) }
        migrateDatabase()

        configAndStartBackgroundWorker()

        webserver = createWebserver().also {
            it.start(wait = runAsDeamon)
        }
    }

    fun shutdown() {
        webserver.stop(1000, 1000)
        get<BakgrunnsjobbService>().stop()
        stopKoin()
    }

    private fun createWebserver(): EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(
            factory = Netty,
            environment = applicationEnvironment {
                config = appConfig
            },
            configure = {
                connector {
                    port = this@FritakAgpApplication.port
                }
            },
            module = {
                if (env is Env.Local) {
                    localAuthTokenDispenser(env)
                }
                nais()
                fritakModule(env)
            }
        )

    private fun configAndStartBackgroundWorker() {
        get<BakgrunnsjobbService>().apply {
            registrer(get<GravidSoeknadProcessor>())
            registrer(get<GravidSoeknadKvitteringProcessor>())

            registrer(get<GravidKravProcessor>())
            registrer(get<GravidKravKvitteringProcessor>())
            registrer(get<GravidKravSlettProcessor>())
            registrer(get<GravidKravEndreProcessor>())

            registrer(get<KroniskSoeknadProcessor>())
            registrer(get<KroniskSoeknadKvitteringProcessor>())

            registrer(get<KroniskKravProcessor>())
            registrer(get<KroniskKravKvitteringProcessor>())
            registrer(get<KroniskKravSlettProcessor>())
            registrer(get<KroniskKravEndreProcessor>())

            registrer(get<BrukernotifikasjonProcessorNy>())
            registrer(get<BrukernotifikasjonProcessor>())

            registrer(get<ArbeidsgiverNotifikasjonProcessor>())
            registrer(get<ArbeidsgiverOppdaterNotifikasjonProcessor>())

            startAsync(true)
        }
    }

    private fun migrateDatabase() {
        logger.info("Starter databasemigrering")

        Flyway.configure().baselineOnMigrate(true)
            .dataSource(GlobalContext.getKoinApplicationOrNull()?.koin?.get())
            .load()
            .migrate()

        logger.info("Databasemigrering slutt")
    }
}

fun main() {
    val logger = LoggerFactory.getLogger("fritakagp")
    Thread.currentThread().setUncaughtExceptionHandler { thread, err ->
        logger.error("uncaught exception in thread ${thread.name}: ${err.message}", err)
    }

    val application = FritakAgpApplication()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.info("Fikk shutdown-signal, avslutter...")
            application.shutdown()
            logger.info("Avsluttet OK")
        }
    )
}
