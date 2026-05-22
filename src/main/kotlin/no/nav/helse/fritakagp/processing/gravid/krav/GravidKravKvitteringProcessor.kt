package no.nav.helse.fritakagp.processing.gravid.krav

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.GravidKravMetrics
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.kafka.DialogMelding
import no.nav.helse.fritakagp.kafka.DialogMeldingMedEndring
import no.nav.helse.fritakagp.kafka.DialogSender
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.log.logger
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class GravidKravKvitteringProcessor(
    private val db: GravidKravRepository,
    private val om: ObjectMapper,
    private val dialogSender: DialogSender
) : BakgrunnsjobbProsesserer {
    companion object {
        const val JOB_TYPE = "gravid-krav-altinn-kvittering"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        val kvitteringJobbData = om.readValue(jobb.data, Jobbdata::class.java)
        val krav =
            db.getById(kvitteringJobbData.kravId)
                ?: throw IllegalArgumentException("Fant ikke kravet i jobbdataene ${jobb.data}")

        val navn = krav.navn ?: "Ukjent"
        val id = krav.id
        val orgnr = Orgnr(krav.virksomhetsnummer)
        val fnr = krav.identitetsnummer

        val gravidKrav =
            when (krav.status) {
                KravStatus.OPPRETTET -> {
                    DialogMelding(
                        type = DialogMelding.Type.GravidKravOpprettet,
                        id = id,
                        orgnr = orgnr,
                        navn = navn,
                        fnr = fnr
                    )
                }

                KravStatus.OPPDATERT -> {
                    DialogMeldingMedEndring(
                        type = DialogMeldingMedEndring.Type.GravidKravEndret,
                        id = id,
                        orgnr = orgnr,
                        navn = navn,
                        fnr = fnr,
                        forrigeKrav = requireNotNull(kvitteringJobbData.forrigeKrav) {
                            "forrigeKrav må være satt for status OPPDATERT"
                        }
                    )
                }

                KravStatus.SLETTET -> {
                    DialogMelding(
                        type = DialogMelding.Type.GravidKravSlettet,
                        id = id,
                        orgnr = orgnr,
                        navn = navn,
                        fnr = fnr
                    )
                }

                else -> {
                    throw IllegalArgumentException("Ugyldig kravstatus for kvittering for ${krav.id} med ${krav.status}")
                }
            }

        val melding = when (gravidKrav) {
            is DialogMelding -> gravidKrav.toJsonStr(DialogMelding.serializer())
            is DialogMeldingMedEndring -> gravidKrav.toJsonStr(DialogMeldingMedEndring.serializer())
            else -> throw IllegalArgumentException("Ugyldig meldingstype for krevId:$gravidKrav")
        }
        logger().info("Sender gravid krav kvittering for krav ${krav.id} til dialogporten")
        dialogSender.sendMessage(melding)

        GravidKravMetrics.tellKvitteringSendt()
    }

    data class Jobbdata(
        val kravId: UUID,
        val forrigeKrav: UUID? = null
    )
}
