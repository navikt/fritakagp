package no.nav.helse.fritakagp.processing.brukernotifikasjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.tms.varsel.action.EksternKanal
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder
import java.time.ZonedDateTime

class BrukernotifikasjonService(
    private val om: ObjectMapper,
    private val sensitivitetNivaa: Sensitivitet = Sensitivitet.High,
    private val frontendAppBaseUrl: String = "https://arbeidsgiver.nav.no/fritak-agp"
) {
    private val logger = this.logger()
    fun opprettVarsel(varselId: String, jobb: Bakgrunnsjobb): String {
        val jobbData = om.readValue<BrukernotifikasjonJobbdata>(jobb.data)
        logger.info("Brukernotifikasjon: Oppretter notifikasjon for ${jobbData.skjemaType} type: ${jobbData.notifikasjonsType} med id: ${jobbData.skjemaId}")
        return VarselActionBuilder.opprett {
            type = Varseltype.Beskjed
            this.varselId = varselId
            sensitivitet = sensitivitetNivaa
            ident = jobbData.identitetsnummer
            tekst = Tekst(
                spraakkode = "nb",
                tekst = jobbData.hentTekst(),
                default = true
            )
            link = frontendAppBaseUrl + jobbData.hentLenke()
            aktivFremTil = ZonedDateTime.now().plusDays(31)
            eksternVarsling {
                preferertKanal = EksternKanal.SMS
            }
        }
    }
}
