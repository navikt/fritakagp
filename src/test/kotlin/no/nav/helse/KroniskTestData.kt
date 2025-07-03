package no.nav.helse

import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.OpprettOppgaveResponse
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.Prioritet
import no.nav.helse.arbeidsgiver.integrasjoner.oppgave2.Status
import no.nav.helse.fritakagp.domain.Arbeidsgiverperiode
import no.nav.helse.fritakagp.domain.FravaerData
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.domain.KroniskSoeknad
import no.nav.helse.fritakagp.web.api.resreq.KroniskKravRequest
import no.nav.helse.fritakagp.web.api.resreq.KroniskSoknadRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.random.Random

object KroniskTestData {
    const val validIdentitetsnummer = "20015001543"
    const val validOrgNr = "917404437"
    const val validSendtAvNavn = "Ola M Avsender"
    const val antallPerioder = 3

    val soeknadKronisk = KroniskSoeknad(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        fravaer = generateFravaersdata(),
        antallPerioder = antallPerioder,
        bekreftet = true,
        sendtAv = validIdentitetsnummer,
        sendtAvNavn = validSendtAvNavn,
        navn = validSendtAvNavn
    )

    val fullValidRequest = KroniskSoknadRequest(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        fravaer = generateFravaersdata(),
        bekreftet = true,
        antallPerioder = antallPerioder,
        dokumentasjon = null,
        ikkeHistoriskFravaer = false
    )

    val kroniskSoknadMedFil = fullValidRequest.copy(
        dokumentasjon = """
                data:image/pdf;base64,TG9yZW0gaXBzdW0gZG9sb3Igc2l0IGFtZXQsIGNvbnNlY3RldHVyIGFkaXBpc2NpbmcgZWxpdC4gQWxpcXVhbSB2aXRhZSBlcm9zIGEgZmVsaXMgbGFjaW5pYSBzb2xsaWNpdHVkaW4gdXQgZWdldCB0b3J0b3IuIFBoYXNlbGx1cyB2ZWhpY3VsYSBlZ2VzdGFzIG1hdHRpcy4gTnVuYyBldSBsaWJlcm8gdWxsYW1jb3JwZXIsIHBsYWNlcmF0IHNhcGllbiBlZ2V0LCBhY2N1bXNhbiBwdXJ1cy4gTWFlY2VuYXMgbWF4aW11cywgcHVydXMgbmVjIGxhY2luaWEgcHVsdmluYXIsIGR1aSBlbmltIGlhY3VsaXMgZGlhbSwgcXVpcyB2aXZlcnJhIG1hc3NhIGxpZ3VsYSBzaXQgYW1ldCBudWxsYS4gU2VkIG1heGltdXMgZXVpc21vZCBhbnRlIGluIHBvc3VlcmUuIFN1c3BlbmRpc3NlIGxpZ3VsYSB0ZWxsdXMsIGZpbmlidXMgdmVsIHBsYWNlcmF0IGlkLCBtYXhpbXVzIHNlZCBhbnRlLiBGdXNjZSBzaXQgYW1ldCBmZXJtZW50dW0gbWFnbmEuCgpDbGFzcyBhcHRlbnQgdGFjaXRpIHNvY2lvc3F1IGFkIGxpdG9yYSB0b3JxdWVudCBwZXIgY29udWJpYSBub3N0cmEsIHBlciBpbmNlcHRvcyBoaW1lbmFlb3MuIERvbmVjIGV1IHRvcnRvciBtYWxlc3VhZGEsIHVsbGFtY29ycGVyIG5pc2wgYXQsIHZ1bHB1dGF0ZSBlc3QuIFZpdmFtdXMgaWQgbG9yZW0gZWdlc3RhcyBhcmN1IHNvZGFsZXMgc2VtcGVyIHZpdGFlIHZlc3RpYnVsdW0gZG9sb3IuIENyYXMgZGFwaWJ1cywgZXJhdCBuZWMgZmF1Y2lidXMgZGFwaWJ1cywgZHVpIHZlbGl0IG9ybmFyZSB0ZWxsdXMsIHF1aXMgdWx0cmljaWVzIGxlbyB0ZWxsdXMgdXQgZXJhdC4gTWFlY2VuYXMgcG9ydGEgdGluY2lkdW50IHBsYWNlcmF0LiBDcmFzIGRpZ25pc3NpbSBsZWN0dXMgdGVsbHVzLCBldCBpbnRlcmR1bSByaXN1cyBwZWxsZW50ZXNxdWUgYXVjdG9yLiBJbiBtYXhpbXVzIGxhY2luaWEgbGVjdHVzLCBhIHNvZGFsZXMgbnVsbGEgdmFyaXVzIGdyYXZpZGEuIEV0aWFtIGhlbmRyZXJpdCBhdWd1ZSBvZGlvLCB2ZWwgcGhhcmV0cmEgb3JjaSBtYWxlc3VhZGEgbmVjLiBQZWxsZW50ZXNxdWUgaGFiaXRhbnQgbW9yYmkgdHJpc3RpcXVlIHNlbmVjdHVzIGV0IG5ldHVzIGV0IG1hbGVzdWFkYSBmYW1lcyBhYyB0dXJwaXMgZWdlc3Rhcy4gU2VkIGV0IGNvbmRpbWVudHVtIG9yY2ksIHZlbCBtYWxlc3VhZGEgbmVxdWUu
        """.trimIndent()
    )

    val kroniskKravRequestValid = KroniskKravRequest(
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = listOf(
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                2,
                månedsinntekt = 2590.8
            )
        ),
        bekreftet = true,
        kontrollDager = null,
        antallDager = 4
    )

    val kroniskKravRequestInValid = KroniskKravRequest(
        virksomhetsnummer = GravidTestData.validOrgNr,
        identitetsnummer = GravidTestData.validIdentitetsnummer,

        perioder = listOf(
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 15),
                LocalDate.of(2020, 1, 10),
                2,
                månedsinntekt = 2590.8
            ),
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 4),
                2,
                månedsinntekt = 3590.8
            ),
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 14),
                12,
                månedsinntekt = 1590.8
            )
        ),

        bekreftet = true,
        kontrollDager = null,
        antallDager = 4
    )

    val kroniskKrav = KroniskKrav(
        opprettet = LocalDateTime.of(2023, 12, 24, 10, 0),
        sendtAv = validIdentitetsnummer,
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = listOf(
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                5,
                månedsinntekt = 2590.8
            )
        ),
        kontrollDager = null,
        antallDager = 4,
        sendtAvNavn = validSendtAvNavn,
        navn = validSendtAvNavn
    )

    val kroniskLangtKrav = KroniskKrav(
        sendtAv = validIdentitetsnummer,
        virksomhetsnummer = validOrgNr,
        identitetsnummer = validIdentitetsnummer,
        perioder = listOf(
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                5,
                månedsinntekt = 2590.8
            ),
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                5,
                månedsinntekt = 2590.8
            ),
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                5,
                månedsinntekt = 2590.8
            ),
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                5,
                månedsinntekt = 2590.8
            ),
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                5,
                månedsinntekt = 2590.8
            ),
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                5,
                månedsinntekt = 2590.8
            ),
            Arbeidsgiverperiode(
                LocalDate.of(2020, 1, 5),
                LocalDate.of(2020, 1, 10),
                5,
                månedsinntekt = 2590.8
            )
        ),
        kontrollDager = null,
        antallDager = 4,
        sendtAvNavn = validSendtAvNavn,
        navn = validSendtAvNavn
    )

    val kroniskOpprettOppgaveResponse = OpprettOppgaveResponse(
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

private fun generateFravaersdata() = (0..24L)
    .map { FravaerData(LocalDate.now().minusMonths(it).toYearMonthString(), Random.nextFloat() * 28) }
    .toMutableSet()

fun LocalDate.toYearMonthString(): String = this.format(DateTimeFormatter.ofPattern("yyyy-MM"))
