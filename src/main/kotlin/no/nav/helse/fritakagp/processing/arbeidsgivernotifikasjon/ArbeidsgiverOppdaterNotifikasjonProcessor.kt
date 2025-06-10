package no.nav.helse.fritakagp.processing.arbeidsgivernotifikasjon

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.runBlocking
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helsearbeidsgiver.arbeidsgivernotifikasjon.ArbeidsgiverNotifikasjonKlient
import no.nav.helsearbeidsgiver.arbeidsgivernotifkasjon.graphql.generated.enums.SaksStatus
import no.nav.helsearbeidsgiver.utils.log.logger
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/* One-off-job som skal oppdatere alle saker med gammel status "under behandling" til ny status "MOTTATT"
* Når alle disse jobbtypene er utført (eller har feilet for godt), kan denne klassen slettes - sjekk det med sql:
* select status, count(status)
* from bakgrunnsjobb
     where type = 'arbeidsgiveroppdaternotifikasjon'
     group by status
*
*/
class ArbeidsgiverOppdaterNotifikasjonProcessor(
    private val gravidKravRepo: GravidKravRepository,
    private val kroniskKravRepo: KroniskKravRepository,
    private val om: ObjectMapper,
    private val arbeidsgiverNotifikasjonKlient: ArbeidsgiverNotifikasjonKlient
) : BakgrunnsjobbProsesserer {
    private val logger = this.logger()

    companion object {
        const val JOB_TYPE = "arbeidsgiveroppdaternotifikasjon"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        logger.info("Prosesserer ${jobb.uuid} med type ${jobb.type}")
        val jobbData = om.readValue<JobbData>(jobb.data)
        val tidspunkt = hentOpprettetTidspunkt(jobbData)
        val resultat = runBlocking {
            arbeidsgiverNotifikasjonKlient.nyStatusSakByGrupperingsid(
                grupperingsid = jobbData.skjemaId.toString(),
                merkelapp = "Fritak arbeidsgiverperiode",
                status = SaksStatus.MOTTATT,
                tidspunkt = tidspunkt.atOffset(ZoneOffset.of("+1")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            )
        }
        logger.info("Oppdaterte sak med ${jobbData.skjemaId} med ref $resultat")
    }

    private fun hentOpprettetTidspunkt(jobbData: JobbData): LocalDateTime {
        if (jobbData.skjemaType == JobbData.SkjemaType.KroniskKrav) {
            val skjema = kroniskKravRepo.getById(jobbData.skjemaId)
                ?: throw IllegalArgumentException("Fant ikke $jobbData")
            return skjema.opprettet
        } else {
            val skjema = gravidKravRepo.getById(jobbData.skjemaId)
                ?: throw IllegalArgumentException("Fant ikke $jobbData")
            return skjema.opprettet
        }
    }
    data class JobbData(
        val skjemaId: UUID,
        val skjemaType: SkjemaType
    ) {
        enum class SkjemaType {
            KroniskKrav,
            GravidKrav
        }
    }
}
