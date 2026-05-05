@file:UseSerializers(UuidSerializer::class)

package no.nav.helse.fritakagp.kafka

import kotlinx.serialization.SerialName
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
    enum class Type {
        @SerialName("GravidSoeknadOpprettet")
        GravidSoeknadOpprettet,

        @SerialName("KroniskSoeknadOpprettet")
        KroniskSoeknadOpprettet,

        @SerialName("KroniskKravOpprettet")
        KroniskKravOpprettet,

        @SerialName("KroniskKravSlettet")
        KroniskKravSlettet,

        @SerialName("GravidKravOpprettet")
        GravidKravOpprettet,

        @SerialName("GravidKravSlettet")
        GravidKravSlettet
    }
}

@Serializable
data class DialogMeldingMedEndring(
    val type: Type,
    val id: UUID,
    val orgnr: Orgnr,
    val navn: String,
    val fnr: String,
    val forrigeKrav: UUID
) {
    @Serializable
    enum class Type {
        GravidKravEndret,
        KroniskKravEndret
    }
}
