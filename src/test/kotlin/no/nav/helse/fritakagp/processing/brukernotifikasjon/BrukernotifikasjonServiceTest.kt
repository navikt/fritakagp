package no.nav.helse.fritakagp.processing.brukernotifikasjon

import io.kotest.matchers.nulls.shouldNotBeNull
import no.nav.helse.fritakagp.customObjectMapper
import no.nav.helse.fritakagp.processing.BakgrunnsJobbUtils
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.NotifikasjonsType.Oppretting
import no.nav.helse.fritakagp.processing.brukernotifikasjon.BrukernotifikasjonJobbdata.SkjemaType
import no.nav.tms.varsel.action.OpprettVarsel
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.BuilderEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class BrukernotifikasjonServiceTest {
    val objectMapper = customObjectMapper()
    val service = BrukernotifikasjonService(objectMapper, Sensitivitet.High)

    @BeforeEach
    fun setup() {
        mapOf(
            "NAIS_APP_NAME" to "test-app",
            "NAIS_NAMESPACE" to "test-namespace",
            "NAIS_CLUSTER_NAME" to "dev"
        ).let { naisEnv ->
            BuilderEnvironment.extend(naisEnv)
        }
    }

    @Test
    fun `opprette varsel`() {
        val skjemaId = UUID.randomUUID()
        val jobData = BrukernotifikasjonJobbdata(
            skjemaId = skjemaId,
            identitetsnummer = "20015001543",
            virksomhetsnavn = "Bedrift",
            skjemaType = SkjemaType.KroniskKrav,
            notifikasjonsType = Oppretting
        )
        val jobDataString = objectMapper.writeValueAsString(jobData)

        val testJob = BakgrunnsJobbUtils.testJob(jobDataString)
        val varselId = UUID.randomUUID().toString()
        val opprettVarselJson = service.opprettVarsel(varselId, testJob)

        val opprettVarsel = objectMapper.readValue(opprettVarselJson, OpprettVarsel::class.java).shouldNotBeNull()

        assertEquals(Varseltype.Beskjed, opprettVarsel.type, "Type skal stemme")
        assertEquals(varselId, opprettVarsel.varselId, "Varsel ID skal stemme")
        assertEquals(jobData.identitetsnummer, opprettVarsel.ident, "Identitetsnummer skal stemme")
        assertEquals(Sensitivitet.High, opprettVarsel.sensitivitet, "Sensitivitet skal stemme")
        assertTrue(opprettVarsel.link.toString().endsWith(jobData.hentLenke()), "Link skal stemme")

        assertEquals(1, opprettVarsel.tekster.size, "Tekster størrelse skal stemme")
        assertEquals("nb", opprettVarsel.tekster[0].spraakkode, "Språkkode skal stemme")
        assertEquals("Bedrift har søkt om at NAV dekker sykepenger fra første dag av sykefraværet ditt.", opprettVarsel.tekster[0].tekst, "Tekst skal stemme")
        assertTrue(opprettVarsel.tekster[0].default, "Default skal være true")
        assertNotNull(opprettVarsel.aktivFremTil, "AktivFremTil skal ikke være null")
        assertEquals("dev", opprettVarsel.produsent.cluster, "Cluster skal stemme")
        assertEquals("test-namespace", opprettVarsel.produsent.namespace, "Namespace skal stemme")
        assertEquals("test-app", opprettVarsel.produsent.appnavn, "Appnavn skal stemme")
    }
}
