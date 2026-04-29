@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helse.fritakagp.kafka

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

@Serializable
sealed class DialogMeldingType {
    @Serializable
    data object GravidSoeknadOpprettet : DialogMeldingType()

    @Serializable
    data object KroniskSoeknadOpprettet : DialogMeldingType()

    @Serializable
    data object KroniskKravOpprettet : DialogMeldingType()

    @Serializable
    data class KroniskKravEndret(val forrigeKrav: UUID) : DialogMeldingType()

    @Serializable
    data object KroniskKravSlettet : DialogMeldingType()

    @Serializable
    data object GravidKravOpprettet : DialogMeldingType()

    @Serializable data class GravidKravEndret(val forrigeKrav: UUID) : DialogMeldingType()

    @Serializable data object GravidKravSlettet : DialogMeldingType()
}

@Serializable
data class DialogMelding(
    val type: DialogMeldingType,
    val id: UUID,
    val orgnr: Orgnr,
    val navn: String,
    val fnr: String
)
