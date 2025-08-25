package no.nav.helse.fritakagp.integration

import no.nav.helsearbeidsgiver.brreg.BrregClient
import no.nav.helsearbeidsgiver.utils.wrapper.Orgnr

sealed interface IBrregService {
    suspend fun hentOrganisasjonNavn(orgnr: String): String
    suspend fun erOrganisasjon(orgnr: String): Boolean
}

class BrregService(
    private val brregClient: BrregClient
) : IBrregService {
    override suspend fun hentOrganisasjonNavn(orgnr: String): String {
        val gyldigOrgnr = Orgnr(orgnr)
        return brregClient.hentOrganisasjonNavn(setOf(orgnr))[gyldigOrgnr] ?: "Ukjent arbeidsgiver"
    }

    override suspend fun erOrganisasjon(orgnr: String): Boolean =
        brregClient.erOrganisasjon(orgnr)
}

class MockBrregService : IBrregService {
    override suspend fun hentOrganisasjonNavn(orgnr: String): String =
        "Stark Industries"

    override suspend fun erOrganisasjon(orgnr: String): Boolean =
        true
}
