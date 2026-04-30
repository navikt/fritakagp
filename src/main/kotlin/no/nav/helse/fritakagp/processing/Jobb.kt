package no.nav.helse.fritakagp.processing

import java.util.UUID

sealed interface Jobb {
    val kravId: UUID
}

data class Jobbdata(
    override val kravId: UUID
) : Jobb

data class JobbdataMedEndring(
    override val kravId: UUID,
    val forrigeKrav: UUID
) : Jobb
