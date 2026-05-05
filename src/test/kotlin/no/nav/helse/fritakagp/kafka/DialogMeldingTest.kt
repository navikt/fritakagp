package no.nav.helse.fritakagp.kafka

import kotlinx.serialization.json.Json
import no.nav.helsearbeidsgiver.utils.json.toJsonStr
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

internal class DialogMeldingTest {

    @Test
    fun `serialiserer opprettet-melding med flat type`() {
        val melding = DialogMelding(
            type = DialogMelding.Type.GravidSoeknadOpprettet,
            id = UUID.fromString("31c825b2-8664-4abb-a173-694a485a81aa"),
            orgnr = Orgnr("214398982"),
            navn = "RETTFERDIG LEVEREGEL",
            fnr = "09419516977"
        )

        val json = melding.toJsonStr(DialogMelding.serializer())

        assertEquals(
            """{"type":"GravidSoeknadOpprettet","id":"31c825b2-8664-4abb-a173-694a485a81aa","orgnr":"214398982","navn":"RETTFERDIG LEVEREGEL","fnr":"09419516977"}""",
            json
        )
    }

    @Test
    fun `serialiserer endret-melding med obligatorisk forrigeKrav`() {
        val melding = DialogMeldingMedEndring(
            type = DialogMeldingMedEndring.Type.GravidKravEndret,
            id = UUID.fromString("31c825b2-8664-4abb-a173-694a485a81aa"),
            orgnr = Orgnr("214398982"),
            navn = "RETTFERDIG LEVEREGEL",
            fnr = "09419516977",
            forrigeKrav = UUID.fromString("8f063f0d-12aa-4fa8-9e34-a76752513eb8")
        )

        val json = melding.toJsonStr(DialogMeldingMedEndring.serializer())

        assertEquals(
            """{"type":"GravidKravEndret","id":"31c825b2-8664-4abb-a173-694a485a81aa","orgnr":"214398982","navn":"RETTFERDIG LEVEREGEL","fnr":"09419516977","forrigeKrav":"8f063f0d-12aa-4fa8-9e34-a76752513eb8"}""",
            json
        )
    }

    @Test
    fun `deserialiserer endret-melding med forrigeKrav`() {
        val json = """{"type":"KroniskKravEndret","id":"31c825b2-8664-4abb-a173-694a485a81aa","orgnr":"214398982","navn":"RETTFERDIG LEVEREGEL","fnr":"09419516977","forrigeKrav":"8f063f0d-12aa-4fa8-9e34-a76752513eb8"}"""

        val melding = Json.decodeFromString(DialogMeldingMedEndring.serializer(), json)

        assertEquals(DialogMeldingMedEndring.Type.KroniskKravEndret, melding.type)
        assertEquals(UUID.fromString("8f063f0d-12aa-4fa8-9e34-a76752513eb8"), melding.forrigeKrav)
    }
}
