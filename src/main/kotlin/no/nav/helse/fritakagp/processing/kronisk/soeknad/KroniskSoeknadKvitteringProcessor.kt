package no.nav.helse.fritakagp.processing.kronisk.soeknad

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.KroniskSoeknadMetrics
import no.nav.helse.fritakagp.db.KroniskSoeknadRepository
import no.nav.helse.fritakagp.kafka.DialogMelding
import no.nav.helse.fritakagp.kafka.DialogSender
import no.nav.helse.fritakagp.kafka.KroniskSoeknadOpprettet
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

class KroniskSoeknadKvitteringProcessor(
    private val kroniskSoeknadKvitteringSender: KroniskSoeknadKvitteringSender,
    private val db: KroniskSoeknadRepository,
    private val om: ObjectMapper,
    private val dialogSender: DialogSender
) : BakgrunnsjobbProsesserer {

    companion object {
        const val JOB_TYPE = "kronisk-søknad-altinn-kvittering"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        val kvitteringJobbData: Jobbdata = om.readValue(jobb.data)
        val soeknad = db.getById(kvitteringJobbData.soeknadId)
            ?: throw IllegalArgumentException("Fant ikke søknaden i jobbdatanene ${jobb.data}")
        val navn = soeknad.navn ?: "Ukjent"
        val kroniskSoeknad = KroniskSoeknadOpprettet(soeknad.id, Orgnr(soeknad.virksomhetsnummer), navn, soeknad.identitetsnummer)

        dialogSender.sendMessage(kroniskSoeknad.toJsonStr(DialogMelding.serializer()))

        // TODO denne fjernes når vi går over til Dialogporten
        kroniskSoeknadKvitteringSender.send(soeknad)

        KroniskSoeknadMetrics.tellKvitteringSendt()
    }

    data class Jobbdata(
        val soeknadId: UUID
    )
}
