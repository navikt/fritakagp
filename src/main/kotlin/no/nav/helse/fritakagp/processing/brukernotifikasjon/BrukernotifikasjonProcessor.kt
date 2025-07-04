package no.nav.helse.fritakagp.processing.brukernotifikasjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.integration.kafka.BrukernotifikasjonSender
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.tms.varsel.action.Sensitivitet
import no.nav.tms.varsel.action.Tekst
import no.nav.tms.varsel.action.Varseltype
import no.nav.tms.varsel.builder.VarselActionBuilder
import java.time.ZonedDateTime
import java.util.UUID

class BrukernotifikasjonProcessor(
    private val gravidKravRepo: GravidKravRepository,
    private val gravidSoeknadRepo: GravidSoeknadRepository,
    private val kroniskKravRepo: KroniskKravRepository,
    private val kroniskSoeknadRepo: KroniskSoeknadRepository,
    private val om: ObjectMapper,
    private val brukernotifikasjonSender: BrukernotifikasjonSender,
    private val sensitivitetNivaa: Sensitivitet = Sensitivitet.High,
    private val frontendAppBaseUrl: String = "https://arbeidsgiver.nav.no/fritak-agp"
) : BakgrunnsjobbProsesserer {
    override val type: String get() = JOB_TYPE
    private val logger = this.logger()
    val ukjentArbeidsgiver = "Arbeidsgiveren din"

    companion object {
        const val JOB_TYPE = "brukernotifikasjon"
    }

    override fun prosesser(jobb: Bakgrunnsjobb) {
        logger.info("Prosesserer ${jobb.uuid} med type ${jobb.type}")

        val varselId = UUID.randomUUID().toString()
        val varsel = opprettVarsel(varselId = varselId, jobb = jobb)
        brukernotifikasjonSender.sendMessage(varselId = varselId, varsel = varsel)
    }

    private fun opprettVarsel(varselId: String, jobb: Bakgrunnsjobb): String {
        val jobbData = om.readValue<Jobbdata>(jobb.data)

        return when (jobbData.skjemaType) {
            Jobbdata.SkjemaType.KroniskKrav -> {
                val skjema = kroniskKravRepo.getById(jobbData.skjemaId) ?: throw IllegalArgumentException("Fant ikke $jobbData")
                getVarsel(
                    varselId = varselId,
                    identitetsnummer = skjema.identitetsnummer,
                    virksomhetsnavn = skjema.virksomhetsnavn,
                    lenke = "$frontendAppBaseUrl/nb/notifikasjon/kronisk/krav/${skjema.id}"
                )
            }

            Jobbdata.SkjemaType.KroniskSøknad -> {
                val skjema = kroniskSoeknadRepo.getById(jobbData.skjemaId) ?: throw IllegalArgumentException("Fant ikke $jobbData")
                getVarsel(
                    varselId = varselId,
                    identitetsnummer = skjema.identitetsnummer,
                    virksomhetsnavn = skjema.virksomhetsnavn,
                    lenke = "$frontendAppBaseUrl/nb/notifikasjon/kronisk/soknad/${skjema.id}"
                )
            }

            Jobbdata.SkjemaType.GravidKrav -> {
                val skjema = gravidKravRepo.getById(jobbData.skjemaId) ?: throw IllegalArgumentException("Fant ikke $jobbData")
                getVarsel(
                    varselId = varselId,
                    identitetsnummer = skjema.identitetsnummer,
                    virksomhetsnavn = skjema.virksomhetsnavn,
                    lenke = "$frontendAppBaseUrl/nb/notifikasjon/gravid/krav/${skjema.id}"
                )
            }

            Jobbdata.SkjemaType.GravidSøknad -> {
                val skjema = gravidSoeknadRepo.getById(jobbData.skjemaId) ?: throw IllegalArgumentException("Fant ikke $jobbData")
                getVarsel(
                    varselId = varselId,
                    identitetsnummer = skjema.identitetsnummer,
                    virksomhetsnavn = skjema.virksomhetsnavn,
                    lenke = "$frontendAppBaseUrl/nb/notifikasjon/gravid/soknad/${skjema.id}"
                )
            }
        }
    }

    private fun getVarsel(varselId: String, identitetsnummer: String, virksomhetsnavn: String?, lenke: String) =
        VarselActionBuilder.opprett {
            type = Varseltype.Beskjed
            this.varselId = varselId
            sensitivitet = sensitivitetNivaa
            ident = identitetsnummer
            tekst = Tekst(
                spraakkode = "nb",
                tekst = "${virksomhetsnavn ?: ukjentArbeidsgiver} har søkt om utvidet støtte fra NAV angående sykepenger til deg.",
                default = true
            )
            link = lenke
            aktivFremTil = ZonedDateTime.now().plusDays(31)
        }

    data class Jobbdata(
        val skjemaId: UUID,
        val skjemaType: SkjemaType
    ) {
        enum class SkjemaType {
            KroniskKrav,
            KroniskSøknad,
            GravidKrav,
            GravidSøknad
        }
    }
}
