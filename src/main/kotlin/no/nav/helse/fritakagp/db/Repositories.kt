package no.nav.helse.fritakagp.db

import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.GravidSoeknad
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.domain.KroniskSoeknad

interface GravidSoeknadRepository : SimpleJsonbRepository<GravidSoeknad>

interface GravidKravRepository : SimpleJsonbRepository<GravidKrav>

interface KroniskSoeknadRepository : SimpleJsonbRepository<KroniskSoeknad>

interface KroniskKravRepository : SimpleJsonbRepository<KroniskKrav>
