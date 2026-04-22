@file:UseSerializers(LocalDateSerializer::class, UuidSerializer::class)

package no.nav.helse.fritakagp.kafka

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import no.nav.helsearbeidsgiver.utils.json.serializer.LocalDateSerializer
import no.nav.helsearbeidsgiver.utils.json.serializer.UuidSerializer
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import java.util.UUID

@Serializable
sealed class DialogMelding

@Serializable
sealed class FritakKravMelding : DialogMelding() {
    abstract val id: UUID
    abstract val orgnr: Orgnr
    abstract val navn: String
    abstract val fnr: String
}

@Serializable
sealed class FritakSoeknadMelding : DialogMelding() {
    abstract val id: UUID
    abstract val orgnr: Orgnr
    abstract val navn: String
    abstract val fnr: String
}

@Serializable
@SerialName("GravidSoeknadOpprettet")
data class GravidSoeknadOpprettet(
    override val id: UUID,
    override val orgnr: Orgnr,
    override val navn: String,
    override val fnr: String
) : FritakSoeknadMelding()

@Serializable
@SerialName("KroniskSoeknadOpprettet")
data class KroniskSoeknadOpprettet(
    override val id: UUID,
    override val orgnr: Orgnr,
    override val navn: String,
    override val fnr: String
) : FritakSoeknadMelding()

@Serializable
@SerialName("KroniskKravOpprettet")
data class KroniskKravOpprettet(
    override val id: UUID,
    override val orgnr: Orgnr,
    override val navn: String,
    override val fnr: String
) : FritakKravMelding()

@Serializable
@SerialName("KroniskKravEndret")
data class KroniskKravEndret(
    override val id: UUID,
    override val orgnr: Orgnr,
    override val navn: String,
    override val fnr: String,
    val forrigeKrav: UUID
) : FritakKravMelding()

@Serializable
@SerialName("KroniskKravSlettet")
data class KroniskKravSlettet(
    override val id: UUID,
    override val orgnr: Orgnr,
    override val navn: String,
    override val fnr: String
) : FritakKravMelding()

@Serializable
@SerialName("GravidKravOpprettet")
data class GravidKravOpprettet(
    override val id: UUID,
    override val orgnr: Orgnr,
    override val navn: String,
    override val fnr: String
) : FritakKravMelding()

@Serializable
@SerialName("GravidKravEndret")
data class GravidKravEndret(
    override val id: UUID,
    override val orgnr: Orgnr,
    override val navn: String,
    override val fnr: String,
    val forrigeKrav: UUID
) : FritakKravMelding()

@Serializable
@SerialName("GravidKravSlettet")
data class GravidKravSlettet(
    override val id: UUID,
    override val orgnr: Orgnr,
    override val navn: String,
    override val fnr: String
) : FritakKravMelding()
