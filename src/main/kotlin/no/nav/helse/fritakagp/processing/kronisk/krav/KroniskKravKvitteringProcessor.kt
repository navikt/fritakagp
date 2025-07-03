package no.nav.helse.fritakagp.processing.kronisk.krav

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.KroniskKravMetrics
import no.nav.helse.fritakagp.db.KroniskKravRepository
import java.util.UUID

class KroniskKravKvitteringProcessor(
    private val kroniskKravKvitteringSender: KroniskKravKvitteringSender,
    private val db: KroniskKravRepository,
    private val om: ObjectMapper
) : BakgrunnsjobbProsesserer {

    companion object {
        const val JOB_TYPE = "kronisk-krav-altinn-kvittering"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        val kvitteringJobbData = om.readValue(jobb.data, Jobbdata::class.java)
        val krav = db.getById(kvitteringJobbData.kravId)
            ?: throw IllegalArgumentException("Fant ikke kravet i jobbdatanene ${jobb.data}")

        kroniskKravKvitteringSender.send(krav)
        KroniskKravMetrics.tellKvitteringSendt()
    }

    data class Jobbdata(
        val kravId: UUID
    )
}
