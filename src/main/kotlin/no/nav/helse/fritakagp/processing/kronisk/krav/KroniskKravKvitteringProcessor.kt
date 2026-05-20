package no.nav.helse.fritakagp.processing.kronisk.krav

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.KroniskKravMetrics
import no.nav.helse.fritakagp.db.KroniskKravRepository
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.kafka.DialogMelding
import no.nav.helse.fritakagp.kafka.DialogMeldingMedEndring
import no.nav.helse.fritakagp.kafka.DialogSender
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class KroniskKravKvitteringProcessor(
    private val db: KroniskKravRepository,
    private val om: ObjectMapper,
    private val dialogSender: DialogSender
) : BakgrunnsjobbProsesserer {

    companion object {
        const val JOB_TYPE = "kronisk-krav-altinn-kvittering"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        val kvitteringJobbData = om.readValue(jobb.data, Jobbdata::class.java)
        val krav = db.getById(kvitteringJobbData.kravId)
            ?: throw IllegalArgumentException("Fant ikke kravet i jobbdatanene ${jobb.data}")
        val navn = krav.navn ?: "Ukjent"
        val id = krav.id
        val orgnr = Orgnr(krav.virksomhetsnummer)
        val fnr = krav.identitetsnummer

        val kroniskKrav = when (krav.status) {
            KravStatus.OPPRETTET -> DialogMelding(
                type = DialogMelding.Type.KroniskKravOpprettet,
                id = id,
                orgnr = orgnr,
                navn = navn,
                fnr = fnr
            )

            KravStatus.OPPDATERT -> DialogMeldingMedEndring(
                type = DialogMeldingMedEndring.Type.KroniskKravEndret,
                id = id,
                orgnr = orgnr,
                navn = navn,
                fnr = fnr,
                forrigeKrav = requireNotNull(kvitteringJobbData.forrigeKrav) {
                    "forrigeKrav må være satt for ${krav.id} med status ${krav.status} "
                }
            )

            KravStatus.SLETTET -> DialogMelding(
                type = DialogMelding.Type.KroniskKravSlettet,
                id = id,
                orgnr = orgnr,
                navn = navn,
                fnr = fnr
            )

            else -> throw IllegalArgumentException("Ugyldig kravstatus for kravId ${krav.id} kvittering: ${krav.status}")
        }

        val melding = when (kroniskKrav) {
            is DialogMelding -> kroniskKrav.toJsonStr(DialogMelding.serializer())
            is DialogMeldingMedEndring -> kroniskKrav.toJsonStr(DialogMeldingMedEndring.serializer())
            else -> throw IllegalArgumentException("Ugyldig meldingstype")
        }
        logger().info("Sender kronisk krav kvittering for krav ${krav.id} til dialogporten")
        dialogSender.sendMessage(melding)

        KroniskKravMetrics.tellKvitteringSendt()
    }

    data class Jobbdata(
        val kravId: UUID,
        val forrigeKrav: UUID? = null
    )
}
