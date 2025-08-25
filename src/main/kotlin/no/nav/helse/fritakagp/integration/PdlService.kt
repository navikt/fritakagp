package no.nav.helse.fritakagp.integration

import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.pdl.PdlClient

class PdlService(
    private val pdlClient: PdlClient
) {
    fun hentNavn(ident: String): String =
        runBlocking { pdlClient.personNavn(ident) }
            ?.fulltNavn()
            .orEmpty()

    fun hentAktoerId(ident: String): String? =
        runBlocking { pdlClient.hentAktoerID(ident) }
}
