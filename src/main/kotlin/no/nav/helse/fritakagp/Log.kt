package no.nav.helse.fritakagp

import java.util.UUID

object Log {
    fun apiRoute(value: String) = "hag_api_route" to value

    fun soeknadId(value: UUID) = "hag_soeknad_id" to value.toString()

    fun kravId(value: UUID) = "hag_krav_id" to value.toString()

    fun kontekstId(value: UUID) = "hag_kontekst_id" to value.toString()
}
