package no.nav.helse.fritakagp.integration

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import no.nav.helsearbeidsgiver.utils.cache.LocalCache
import java.time.LocalDate
import kotlin.time.Duration.Companion.days

class GrunnbeloepClient(
    private val url: String,
    private val httpClient: HttpClient
) {
    private val cache = LocalCache<GrunnbeloepResponse>(LocalCache.Config(1.days, 5))

    fun hentGrunnbeloep(dato: LocalDate): Int {
        val cacheKey = if (dato.month.value >= 5) "${dato.year}-05" else "${dato.year - 1}-05"
        return runBlocking {
            cache.getOrPut(cacheKey) {
                httpClient.get("$url?dato=$dato").body()
            }
        }
            .grunnbeloep
    }
}

private data class GrunnbeloepResponse(
    val grunnbeloep: Int
)
