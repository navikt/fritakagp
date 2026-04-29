@file:UseSerializers( UuidSerializer::class)

package no.nav.helse.fritakagp.kafka

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

@Serializable
data class DialogMelding(
    val type: Type,
    val id: UUID,
    val orgnr: Orgnr,
    val navn: String,
    val fnr: String
) {
    @Serializable
    sealed class Type {
        @Serializable
        data object GravidSoeknadOpprettet : Type()

        @Serializable
        data object KroniskSoeknadOpprettet : Type()

        @Serializable
        data object KroniskKravOpprettet : Type()

        @Serializable
        data class KroniskKravEndret(val forrigeKrav: UUID) : Type()

        @Serializable
        data object KroniskKravSlettet : Type()

        @Serializable
        data object GravidKravOpprettet : Type()

        @Serializable
        data class GravidKravEndret(val forrigeKrav: UUID) : Type()

        @Serializable
        data object GravidKravSlettet : Type()
    }
}
