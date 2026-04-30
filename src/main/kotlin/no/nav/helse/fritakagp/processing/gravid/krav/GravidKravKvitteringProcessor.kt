package no.nav.helse.fritakagp.processing.gravid.krav

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.GravidKravMetrics
import no.nav.helse.fritakagp.db.GravidKravRepository
import no.nav.helse.fritakagp.domain.KravStatus
import no.nav.helse.fritakagp.kafka.DialogSender
import no.nav.helse.fritakagp.kafka.FritakKravMelding
import no.nav.helse.fritakagp.kafka.GravidKravEndret
import no.nav.helse.fritakagp.kafka.GravidKravOpprettet
import no.nav.helse.fritakagp.kafka.GravidKravSlettet
import no.nav.helse.fritakagp.processing.Jobb
import no.nav.helse.fritakagp.processing.JobbdataMedEndring
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import kotlin.jvm.java

class GravidKravKvitteringProcessor(
    private val gravidKravKvitteringSender: GravidKravKvitteringSender,
    private val db: GravidKravRepository,
    private val om: ObjectMapper,
    private val dialogSender: DialogSender
) : BakgrunnsjobbProsesserer {
    companion object {
        const val JOB_TYPE = "gravid-krav-altinn-kvittering"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        val kvitteringJobbData = om.readValue(jobb.data, Jobb::class.java)
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
                    GravidKravOpprettet(id, orgnr, navn, fnr)
                }

                KravStatus.OPPDATERT -> {
                    GravidKravEndret(
                        id,
                        orgnr,
                        navn,
                        fnr,
                        forrigeKrav =
                        (kvitteringJobbData as JobbdataMedEndring).forrigeKrav
                    )
                }

                KravStatus.SLETTET -> {
                    GravidKravSlettet(id, orgnr, navn, fnr)
                }

                else -> {
                    throw IllegalArgumentException("Ugyldig kravstatus for kvittering: ${krav.status}")
                }
            }

        dialogSender.sendMessage(gravidKrav.toJsonStr(FritakKravMelding.serializer()))

        // TODO denne fjernes når vi går over til Dialogporten
        gravidKravKvitteringSender.send(krav)

        GravidKravMetrics.tellKvitteringSendt()
    }
}
