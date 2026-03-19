package no.nav.helse.fritakagp.processing.gravid.soeknad

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb
import no.nav.hag.utils.bakgrunnsjobb.BakgrunnsjobbProsesserer
import no.nav.helse.fritakagp.GravidSoeknadMetrics
import no.nav.helse.fritakagp.db.GravidSoeknadRepository
import no.nav.helse.fritakagp.kafka.DialogSender
import java.util.UUID
import kotlin.jvm.java

class GravidSoeknadKvitteringProcessor(
    private val gravidSoeknadKvitteringSender: GravidSoeknadKvitteringSender,
    private val db: GravidSoeknadRepository,
    private val om: ObjectMapper,
    private val dialogProducer: DialogSender
) : BakgrunnsjobbProsesserer {

    companion object {
        const val JOB_TYPE = "gravid-søknad-altinn-kvittering"
    }

    override val type: String get() = JOB_TYPE

    override fun prosesser(jobb: Bakgrunnsjobb) {
        val kvitteringJobbData = om.readValue(jobb.data, Jobbdata::class.java)
        val soeknad = db.getById(kvitteringJobbData.soeknadId)
            ?: throw IllegalArgumentException("Fant ikke søknaden i jobbdatanene ${jobb.data}")

        gravidSoeknadKvitteringSender.send(soeknad)
        GravidSoeknadMetrics.tellKvitteringSendt()
        dialogProducer.sendMessage(
            UUID.randomUUID().toString(),
            """{
            "orgnr": "214398982",
            "sykemeldt": "Ola Nordmann",
            "type": "GravidSoeknadMelding"
        }"""
        )
    }

    data class Jobbdata(
        val soeknadId: UUID
    )
}
