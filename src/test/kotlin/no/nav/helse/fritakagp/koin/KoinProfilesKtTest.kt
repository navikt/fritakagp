package no.nav.helse.fritakagp.koin

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import no.altinn.services.serviceengine.correspondence._2009._10.ICorrespondenceAgencyExternalBasic
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbRepository
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbService
import no.nav.helse.fritakagp.Env
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.domain.BeloepBeregning
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonSender
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverOppdaterNotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessor
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonProcessorNy
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonService
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.krav.GravidKravKvitteringSender
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.gravid.soeknad.GravidSoeknadKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravEndreProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravKvitteringSender
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravProcessor
import no.nav.helse.fritakagp.processing.kronisk.krav.KroniskKravSlettProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringProcessor
import no.nav.helse.fritakagp.processing.kronisk.soeknad.KroniskSoeknadKvitteringSender
import no.nav.helse.fritakagp.service.PdlService
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.brreg.BrregClient
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.inject
import javax.sql.DataSource

@ExtendWith(MockKExtension::class)
class KoinProfilesKtTest : KoinTest {

    private val prod: Env.Prod = mockk(relaxed = true)
    private val preprod: Env.Preprod = mockk(relaxed = true)
    private val local: Env.Local = mockk(relaxed = true)
    private val bucketStorage: BucketStorage = mockk(relaxed = true)

    private val dataSource = mockk<DataSource>(relaxed = true)
    private val brukernotifikasjonSender = mockk<BrukernotifikasjonSender>(relaxed = true)
    private val arbeidsgiverNotifikasjonKlient = mockk<ArbeidsgiverNotifikasjonKlient>(relaxed = true)
    private val iCorrespondenceAgencyExternalBasic = mockk<ICorrespondenceAgencyExternalBasic>(relaxed = true)

    private val gravidSoeknadRepository: GravidSoeknadRepository by inject()
    private val kroniskSoeknadRepository: KroniskSoeknadRepository by inject()
    private val gravidKravRepository: GravidKravRepository by inject()
    private val kroniskKravRepository: KroniskKravRepository by inject()
    private val bakgrunnsjobbRepository: BakgrunnsjobbRepository by inject()
    private val bakgrunnsjobbService: BakgrunnsjobbService by inject()
    private val brukernotifikasjonService: BrukernotifikasjonService by inject()
    private val kroniskKravProcessor: KroniskKravProcessor by inject()
    private val kroniskKravSlettProcessor: KroniskKravSlettProcessor by inject()
    private val kroniskKravEndreProcessor: KroniskKravEndreProcessor by inject()
    private val gravidSoeknadKvitteringSender: GravidSoeknadKvitteringSender by inject()
    private val gravidSoeknadKvitteringProcessor: GravidSoeknadKvitteringProcessor by inject()
    private val gravidKravKvitteringSender: GravidKravKvitteringSender by inject()
    private val gravidKravKvitteringProcessor: GravidKravKvitteringProcessor by inject()
    private val kroniskSoeknadKvitteringSender: KroniskSoeknadKvitteringSender by inject()
    private val kroniskSoeknadKvitteringProcessor: KroniskSoeknadKvitteringProcessor by inject()
    private val kroniskKravKvitteringSender: KroniskKravKvitteringSender by inject()
    private val kroniskKravKvitteringProcessor: KroniskKravKvitteringProcessor by inject()
    private val brukernotifikasjonProcessorNy: BrukernotifikasjonProcessorNy by inject()
    private val brukernotifikasjonProcessor: BrukernotifikasjonProcessor by inject()
    private val arbeidsgiverNotifikasjonProcessor: ArbeidsgiverNotifikasjonProcessor by inject()
    private val arbeidsgiverOppdaterNotifikasjonProcessor: ArbeidsgiverOppdaterNotifikasjonProcessor by inject()
    private val pdlService: PdlService by inject()
    private val brregClient: BrregClient by inject()
    private val beloepBeregning: BeloepBeregning by inject()

    private val objectMapper: ObjectMapper by inject()

    @Test
    fun `test Prod profileModules`() {
        startKoin {
            modules(profileModules(prod) + getTestModules())
        }
        assertKoin()
        stopKoin()
    }

    @Test
    fun `test Preprod profileModules`() {
        startKoin {
            modules(profileModules(preprod) + getTestModules())
        }
        assertKoin()
        stopKoin()
    }

    @Test
    fun `test Local profileModules`() {
        startKoin {
            modules(profileModules(local) + getTestModules())
        }
        assertKoin()
        stopKoin()
    }

    private fun assertKoin() {
        assertNotNull(gravidSoeknadRepository)
        assertNotNull(kroniskSoeknadRepository)
        assertNotNull(gravidKravRepository)
        assertNotNull(kroniskKravRepository)
        assertNotNull(bakgrunnsjobbRepository)
        assertNotNull(bakgrunnsjobbService)
        assertNotNull(brukernotifikasjonService)
        assertNotNull(objectMapper)
        assertNotNull(kroniskKravProcessor)
        assertNotNull(kroniskKravSlettProcessor)
        assertNotNull(kroniskKravEndreProcessor)
        assertNotNull(gravidSoeknadKvitteringSender)
        assertNotNull(gravidSoeknadKvitteringProcessor)
        assertNotNull(gravidKravKvitteringSender)
        assertNotNull(gravidKravKvitteringProcessor)
        assertNotNull(kroniskSoeknadKvitteringSender)
        assertNotNull(kroniskSoeknadKvitteringProcessor)
        assertNotNull(kroniskKravKvitteringSender)
        assertNotNull(kroniskKravKvitteringProcessor)
        assertNotNull(brukernotifikasjonProcessorNy)
        assertNotNull(brukernotifikasjonProcessor)
        assertNotNull(arbeidsgiverNotifikasjonProcessor)
        assertNotNull(arbeidsgiverOppdaterNotifikasjonProcessor)
        assertNotNull(pdlService)
        assertNotNull(brregClient)
        assertNotNull(beloepBeregning)
    }

    private fun getTestModules(): Module {
        val testModule =
            module {
                single { dataSource } bind DataSource::class
                single { bucketStorage } bind BucketStorage::class
                single { brukernotifikasjonSender } bind BrukernotifikasjonSender::class
                single { arbeidsgiverNotifikasjonKlient } bind ArbeidsgiverNotifikasjonKlient::class
                single { iCorrespondenceAgencyExternalBasic } bind ICorrespondenceAgencyExternalBasic::class
            }
        return testModule
    }
}
