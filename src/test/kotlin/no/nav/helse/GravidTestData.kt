package no.nav.helse

import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.GravidSoeknad
import no.nav.helse.fritakagp.domain.Omplassering
import no.nav.helse.fritakagp.domain.OmplasseringAarsak
import no.nav.helse.fritakagp.domain.Tiltak
import no.nav.helse.fritakagp.integration.arbeidsgiver.OpprettOppgaveResponse
import no.nav.helse.fritakagp.integration.arbeidsgiver.Prioritet
import no.nav.helse.fritakagp.integration.arbeidsgiver.Status
import no.nav.helse.fritakagp.web.api.resreq.ArbeidsgiverperiodeRequest
import no.nav.helse.fritakagp.web.api.resreq.GravidKravRequest
import no.nav.helse.fritakagp.web.api.resreq.GravidSoknadRequest
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juni
import java.time.LocalDate
import java.time.LocalDateTime

object GravidTestData {
    const val validNavn = "Personliga Person"
    const val validIdentitetsnummer = "20015001543"
    const val validOrgNr = "917404437"
    const val validSendtAvNavn = "Ola M Avsender"

    val soeknadGravid = GravidSoeknad(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        tilrettelegge = true,
        tiltak = listOf(Tiltak.HJEMMEKONTOR, Tiltak.ANNET),
        tiltakBeskrivelse = """Vi prøvde både det ene og det andre og det første kanskje virka litt men muligens and the andre ikke var så på stell men akk ja sånn lorem
    \t\b\n\r\f\'\"\\
    |44.44% 34 %   %%
    |=><!~?:==<=>=!=&&||++--+-*/&|^%<<>>>>>+=-=*=/=&=|=^=%=<<=>>=>>>=
        //    &"´`'
     |§. áé.  áéíñ A
ĄBCĆDEĘFGHIJKLŁMNŃOÓP(Q)RSŚTU(V)W()YZŹŻ
aąbcćdeęfghijlłmnńoóprsśtuwź

|Gresk
|Αα,Ββ,Γγ,Δδ,Εε,Ζζ,Ηη,Θθ,Ιι,Κκ,Λλ,Μμ,Νν,Ξξ,Οο,Ππ,Ρρ,Σσ/ς,Ττ,Υυ,Φφ,Χχ,Ψψ,Ωω.
| Japansk:
|私わたしワタシ金魚きんぎょキンギョ煙草莨たばこタバコ東京とうきょうトーキョー
|kinesisk
|的 一 是不了人我在
        """.trimMargin(),
        omplassering = Omplassering.IKKE_MULIG,
        omplasseringAarsak = OmplasseringAarsak.HELSETILSTANDEN,
        sendtAv = validIdentitetsnummer,
        termindato = LocalDate.now().plusDays(25),
        sendtAvNavn = validSendtAvNavn,
        navn = validNavn
    )

    val gravidSoeknadSomString = """
        {
          "identitetsnummer": "19429007936",
          "virksomhetsnummer": "810007842",
          "tilrettelegge": true,
          "tiltak": [
            "ANNET",
            "HJEMMEKONTOR"
          ],
          "tiltakBeskrivelse": "Kjefting",
          "omplassering": "NEI",
          "dokumentasjon": "",
          "bekreftet": true,
          "termindato": "2023-12-24"
        }
    """.trimIndent()

    val fullValidSoeknadRequest = GravidSoknadRequest(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        termindato = LocalDate.now().plusDays(25),
        tilrettelegge = true,
        tiltak = listOf(
            Tiltak.ANNET,
            Tiltak.HJEMMEKONTOR,
            Tiltak.TILPASSEDE_ARBEIDSOPPGAVER,
            Tiltak.TILPASSET_ARBEIDSTID
        ),
        tiltakBeskrivelse = "beskrivelse",
        omplassering = Omplassering.NEI,
        omplasseringAarsak = OmplasseringAarsak.HELSETILSTANDEN,
        bekreftet = true,
        dokumentasjon = null
    )

    val gravidSoknadMedFil = GravidSoknadRequest(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        termindato = LocalDate.now().plusDays(25),
        tilrettelegge = true,
        tiltak = listOf(Tiltak.ANNET),
        tiltakBeskrivelse = "beskrivelse",
        omplassering = Omplassering.JA,
        omplasseringAarsak = null,
        bekreftet = true,
        dokumentasjon = """
                data:image/pdf;base64,TG9yZW0gaXBzdW0gZG9sb3Igc2l0IGFtZXQsIGNvbnNlY3RldHVyIGFkaXBpc2NpbmcgZWxpdC4gQWxpcXVhbSB2aXRhZSBlcm9zIGEgZmVsaXMgbGFjaW5pYSBzb2xsaWNpdHVkaW4gdXQgZWdldCB0b3J0b3IuIFBoYXNlbGx1cyB2ZWhpY3VsYSBlZ2VzdGFzIG1hdHRpcy4gTnVuYyBldSBsaWJlcm8gdWxsYW1jb3JwZXIsIHBsYWNlcmF0IHNhcGllbiBlZ2V0LCBhY2N1bXNhbiBwdXJ1cy4gTWFlY2VuYXMgbWF4aW11cywgcHVydXMgbmVjIGxhY2luaWEgcHVsdmluYXIsIGR1aSBlbmltIGlhY3VsaXMgZGlhbSwgcXVpcyB2aXZlcnJhIG1hc3NhIGxpZ3VsYSBzaXQgYW1ldCBudWxsYS4gU2VkIG1heGltdXMgZXVpc21vZCBhbnRlIGluIHBvc3VlcmUuIFN1c3BlbmRpc3NlIGxpZ3VsYSB0ZWxsdXMsIGZpbmlidXMgdmVsIHBsYWNlcmF0IGlkLCBtYXhpbXVzIHNlZCBhbnRlLiBGdXNjZSBzaXQgYW1ldCBmZXJtZW50dW0gbWFnbmEuCgpDbGFzcyBhcHRlbnQgdGFjaXRpIHNvY2lvc3F1IGFkIGxpdG9yYSB0b3JxdWVudCBwZXIgY29udWJpYSBub3N0cmEsIHBlciBpbmNlcHRvcyBoaW1lbmFlb3MuIERvbmVjIGV1IHRvcnRvciBtYWxlc3VhZGEsIHVsbGFtY29ycGVyIG5pc2wgYXQsIHZ1bHB1dGF0ZSBlc3QuIFZpdmFtdXMgaWQgbG9yZW0gZWdlc3RhcyBhcmN1IHNvZGFsZXMgc2VtcGVyIHZpdGFlIHZlc3RpYnVsdW0gZG9sb3IuIENyYXMgZGFwaWJ1cywgZXJhdCBuZWMgZmF1Y2lidXMgZGFwaWJ1cywgZHVpIHZlbGl0IG9ybmFyZSB0ZWxsdXMsIHF1aXMgdWx0cmljaWVzIGxlbyB0ZWxsdXMgdXQgZXJhdC4gTWFlY2VuYXMgcG9ydGEgdGluY2lkdW50IHBsYWNlcmF0LiBDcmFzIGRpZ25pc3NpbSBsZWN0dXMgdGVsbHVzLCBldCBpbnRlcmR1bSByaXN1cyBwZWxsZW50ZXNxdWUgYXVjdG9yLiBJbiBtYXhpbXVzIGxhY2luaWEgbGVjdHVzLCBhIHNvZGFsZXMgbnVsbGEgdmFyaXVzIGdyYXZpZGEuIEV0aWFtIGhlbmRyZXJpdCBhdWd1ZSBvZGlvLCB2ZWwgcGhhcmV0cmEgb3JjaSBtYWxlc3VhZGEgbmVjLiBQZWxsZW50ZXNxdWUgaGFiaXRhbnQgbW9yYmkgdHJpc3RpcXVlIHNlbmVjdHVzIGV0IG5ldHVzIGV0IG1hbGVzdWFkYSBmYW1lcyBhYyB0dXJwaXMgZWdlc3Rhcy4gU2VkIGV0IGNvbmRpbWVudHVtIG9yY2ksIHZlbCBtYWxlc3VhZGEgbmVxdWUu
        """.trimIndent()
    )

    val gravidKravRequestValid = GravidKravRequest(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = listOf(
            ArbeidsgiverperiodeRequest(
                fom = 5.januar(2020),
                tom = 10.januar(2020),
                antallDagerMedRefusjon = 2,
                månedsinntekt = 2590.8,
                gradering = 0.8
            )
        ),
        bekreftet = true,
        kontrollDager = null,
        antallDager = 4
    )

    val gravidKravRequestWithWrongDecimal = GravidKravRequest(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = listOf(
            ArbeidsgiverperiodeRequest(
                fom = 1.juni(2021),
                tom = 12.juni(2020),
                antallDagerMedRefusjon = 5,
                månedsinntekt = 12344.0,
                gradering = 1.0
            )
        ),
        bekreftet = true,
        kontrollDager = null,
        antallDager = 260
    )

    val gravidKravRequestInValid = GravidKravRequest(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = listOf(
            ArbeidsgiverperiodeRequest(
                fom = 15.januar(2020),
                tom = 10.januar(2020),
                antallDagerMedRefusjon = 2,
                månedsinntekt = 2590.8,
                gradering = 1.0
            ),
            ArbeidsgiverperiodeRequest(
                fom = 5.januar(2020),
                tom = 4.januar(2020),
                antallDagerMedRefusjon = 2,
                månedsinntekt = 3590.8,
                gradering = 1.0
            ),
            ArbeidsgiverperiodeRequest(
                fom = 5.januar(2020),
                tom = 14.januar(2020),
                antallDagerMedRefusjon = 12,
                månedsinntekt = 1590.8,
                gradering = 1.0
            )
        ),
        bekreftet = true,
        kontrollDager = null,
        antallDager = 4
    )

    // Innrapportert feil POH-693 Gravid Krav - Backendvalidereing feiler når man sender inn krav for bare en dag.
    val gravidKravRequestValidPeriode1Dag = GravidKravRequest(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = listOf(
            ArbeidsgiverperiodeRequest(
                fom = 1.februar(2020),
                tom = 1.februar(2020),
                antallDagerMedRefusjon = 1,
                månedsinntekt = 123.8,
                gradering = 1.0
            )
        ),
        bekreftet = true,
        kontrollDager = null,
        antallDager = 4
    )

    val gravidKrav = GravidKrav(
        opprettet = LocalDateTime.of(2023, 12, 24, 10, 0),
        sendtAv = validIdentitetsnummer,
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = listOf(mockArbeidsgiverperiode()),
        kontrollDager = null,
        antallDager = 4,
        sendtAvNavn = validSendtAvNavn,
        navn = validNavn
    )

    val gravidEndretKrav = gravidKrav.copy(antallDager = 6)

    val gravidLangtKrav = GravidKrav(
        sendtAv = validIdentitetsnummer,
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = List(7) { mockArbeidsgiverperiode() },
        kontrollDager = null,
        antallDager = 4,
        sendtAvNavn = validSendtAvNavn,
        navn = validNavn
    )

    val gravidOpprettOppgaveResponse = OpprettOppgaveResponse(
        id = 1234,
        tildeltEnhetsnr = "0100",
        tema = "KON",
        oppgavetype = "JFR",
        versjon = 1,
        aktivDato = LocalDate.now(),
        prioritet = Prioritet.NORM,
        status = Status.UNDER_BEHANDLING
    )
}
