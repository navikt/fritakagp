package no.nav.helse.fritakagp

import io.prometheus.client.Counter
import no.nav.hag.utils.bakgrunnsjobb.Bakgrunnsvarsler

const val METRICS_NS = "fritakagp"

class MetrikkVarsler : Bakgrunnsvarsler {
    override fun rapporterPermanentFeiletJobb() {
        FEILET_JOBB_COUNTER.inc()
    }
}

val FEILET_JOBB_COUNTER: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("feilet_jobb")
    .help("Counts the number of permanently failed jobs")
    .register()

object GravidKravMetrics :
    ProseseringsMetrikker("gravid_krav", "Metrikker for krav, gravid")

object KroniskKravMetrics :
    ProseseringsMetrikker("kronisk_krav", "Metrikker for krav, kronisk")

object GravidSoeknadMetrics :
    ProseseringsMetrikker("gravid_soeknad", "Metrikker for søknader, gravid")

object KroniskSoeknadMetrics :
    ProseseringsMetrikker("kronisk_soeknad", "Metrikker for søknader, kronisk")

abstract class ProseseringsMetrikker(metricName: String, metricHelpText: String) {
    private val counter: Counter = Counter.build()
        .namespace(METRICS_NS)
        .name(metricName)
        .labelNames("hendelse")
        .help(metricHelpText)
        .register()

    fun tellMottatt() = counter.labels("mottatt").inc()
    fun tellJournalfoert() = counter.labels("journalfoert").inc()
    fun tellOppgaveOpprettet() = counter.labels("oppgaveOpprettet").inc()
    fun tellKvitteringSendt() = counter.labels("kvitteringSendt").inc()
}
