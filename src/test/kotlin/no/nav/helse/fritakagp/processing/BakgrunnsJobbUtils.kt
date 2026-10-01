package no.nav.helse.fritakagp.processing

import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsjobb

object BakgrunnsJobbUtils {
    fun emptyJob() = Bakgrunnsjobb(data = "", type = "")

    fun testJob(data: String) = Bakgrunnsjobb(data = data, type = "test")
}
