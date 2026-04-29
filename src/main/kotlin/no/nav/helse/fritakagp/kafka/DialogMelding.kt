@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helse.fritakagp.kafka

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

@Serializable
enum class DialogMeldingType {
    GravidSoeknadOpprettet,
    KroniskSoeknadOpprettet,
    KroniskKravOpprettet,
    KroniskKravEndret,
    KroniskKravSlettet,
    GravidKravOpprettet,
    GravidKravEndret,
    GravidKravSlettet
}

@Serializable
data class DialogMelding(
    val type: DialogMeldingType,
    val id: UUID,
    val orgnr: Orgnr,
    val navn: String,
    val fnr: String,
    val forrigeKrav: UUID? = null
)
