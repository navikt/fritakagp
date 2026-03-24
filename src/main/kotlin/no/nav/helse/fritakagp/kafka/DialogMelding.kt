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
@SerialName("GravidSoeknadMelding")
data class GravidSoeknadMelding(
    val id: UUID,
    val orgnr: Orgnr,
    val navn: String?,
    val foedselsdato: String
) : DialogMelding()
