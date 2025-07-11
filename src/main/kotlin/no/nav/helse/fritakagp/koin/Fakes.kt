package no.nav.helse.fritakagp.koin

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveKlient
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OppgaveResponse
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OpprettOppgaveRequest
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OpprettOppgaveResponse
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.Prioritet
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.Status
import no.nav.helse.fritakagp.Issuers
import no.nav.helse.fritakagp.auth.AuthClient
import no.nav.helse.fritakagp.auth.IdentityProvider
import no.nav.helse.fritakagp.auth.TokenResponse
import no.nav.helse.fritakagp.auth.fetchToken
import no.nav.helse.fritakagp.integration.brreg.BrregClient
import no.nav.helse.fritakagp.integration.brreg.MockBrregClient
import no.nav.helse.fritakagp.integration.gcp.BucketStorage
import no.nav.helse.fritakagp.integration.gcp.MockBucketStorage
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonSender
import no.nav.helse.fritakagp.integration.kafka.MockBrukernotifikasjonBeskjedSender
import no.nav.helse.fritakagp.integration.virusscan.MockVirusScanner
import no.nav.helse.fritakagp.integration.virusscan.VirusScanner
import no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon.ArbeidsgiverOppdaterNotifikasjonProcessor
import no.nav.helsearbeidsgiver.aareg.AaregClient
import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.altinn.Altinn3OBOClient
import no.nav.helsearbeidsgiver.altinn.AltinnTilgangRespons
import no.nav.helsearbeidsgiver.dokarkiv.DokArkivClient
import no.nav.helsearbeidsgiver.pdl.PdlClient
import no.nav.helsearbeidsgiver.pdl.domene.FullPerson
import no.nav.helsearbeidsgiver.pdl.domene.PersonNavn
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.koin.core.module.Module
import org.koin.dsl.bind
import java.time.LocalDate
import java.time.LocalDateTime

fun Module.mockExternalDependecies() {
    single { MockOAuth2Server().apply { start(port = 6668) } }
    single {
        mockk<AuthClient> {
            val mockOAuth2Server: MockOAuth2Server = get()
            coEvery { exchange(IdentityProvider.TOKEN_X, any(), any()) } returns
                mockOAuth2Server.issueToken(subject = "", issuerId = Issuers.TOKENX, audience = "").let {
                    TokenResponse.Success(
                        it.serialize(),
                        3599
                    )
                }
            coEvery { token(IdentityProvider.AZURE_AD, any()) } answers {
                mockOAuth2Server.issueToken(subject = "fritakagp", issuerId = "azure", audience = secondArg<String>()).let {
                    TokenResponse.Success(
                        it.serialize(),
                        3599
                    )
                }
            }
        }
    }

    single {
        mockk<Altinn3OBOClient> {
            val json = Json { ignoreUnknownKeys = true }
            val jsonFile = "altinn-mock/rettighetene-til-tanja-minge.json".loadFromResources()
            val tilgangRespons = json.decodeFromString<AltinnTilgangRespons>(jsonFile)

            coEvery { hentHierarkiMedTilganger(any(), any()) } returns tilgangRespons
            coEvery { harTilgangTilOrganisasjon(any(), any(), any()) } answers {
                val organisasjonsNr = secondArg<String>()
                tilgangRespons.tilgangTilOrgNr["4936:1"]?.contains(organisasjonsNr) ?: false
            }
        }
    }

    single { MockBrukernotifikasjonBeskjedSender() } bind BrukernotifikasjonSender::class
    single {
        mockk<AaregClient> {
            coEvery { hentAnsettelsesperioder(any(), any()) } returns mapOf(
                Orgnr("810007842") to setOf(Periode(LocalDate.MIN, null)),
                Orgnr("910098896") to setOf(Periode(LocalDate.MIN, null)),
                Orgnr("917404437") to setOf(Periode(LocalDate.MIN, null))
            )
        }
    }

    single {
        val authClient: AuthClient = get()
        DokArkivClient("url", authClient.fetchToken(IdentityProvider.AZURE_AD, "dokarkiv"))
    } bind DokArkivClient::class

    single {
        mockk<PdlClient> {
            coEvery { personNavn(any()) } returns PersonNavn("Ola", "M", "Avsender")
            coEvery { fullPerson(any()) } returns FullPerson(
                navn = PersonNavn(fornavn = "Per", mellomnavn = "", etternavn = "Ulv"),
                foedselsdato = LocalDate.of(1900, 1, 1),
                ident = "aktør-id",
                diskresjonskode = "SPSF",
                geografiskTilknytning = "SWE"
            )
        }
    }

    single {
        object : OppgaveKlient {
            override suspend fun hentOppgave(oppgaveId: Int, callId: String): OppgaveResponse {
                return OppgaveResponse(oppgaveId, 1, oppgavetype = "JFR", aktivDato = LocalDateTime.now().minusDays(3).toLocalDate(), prioritet = Prioritet.NORM.toString())
            }

            override suspend fun opprettOppgave(
                opprettOppgaveRequest: OpprettOppgaveRequest,
                callId: String
            ): OpprettOppgaveResponse = OpprettOppgaveResponse(
                1234,
                "0100",
                tema = "KON",
                oppgavetype = "JFR",
                versjon = 1,
                aktivDato = LocalDate.now(),
                Prioritet.NORM,
                Status.UNDER_BEHANDLING
            )
        }
    } bind OppgaveKlient::class

    single { MockVirusScanner() } bind VirusScanner::class
    single { MockBucketStorage() } bind BucketStorage::class
    single { MockBrregClient() } bind BrregClient::class

    single { mockk<ArbeidsgiverOppdaterNotifikasjonProcessor>(relaxed = true) }
}
fun String.loadFromResources(): String {
    return ClassLoader.getSystemResource(this).readText()
}
