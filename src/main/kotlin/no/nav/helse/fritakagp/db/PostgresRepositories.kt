package no.nav.helse.fritakagp.db

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.helse.fritakagp.domain.GravidKrav
import no.nav.helse.fritakagp.domain.GravidSoeknad
import no.nav.helse.fritakagp.domain.KroniskKrav
import no.nav.helse.fritakagp.domain.KroniskSoeknad
import javax.sql.DataSource

class PostgresGravidSoeknadRepository(
    ds: DataSource,
    om: ObjectMapper,
) : SimpleJsonbRepositoryBase<GravidSoeknad>("soeknadgravid", ds, om, GravidSoeknad::class.java),
    GravidSoeknadRepository

class PostgresKroniskSoeknadRepository(
    ds: DataSource,
    om: ObjectMapper,
) : SimpleJsonbRepositoryBase<KroniskSoeknad>("soeknadkronisk", ds, om, KroniskSoeknad::class.java),
    KroniskSoeknadRepository

class PostgresGravidKravRepository(
    ds: DataSource,
    om: ObjectMapper,
) : SimpleJsonbRepositoryBase<GravidKrav>("kravgravid", ds, om, GravidKrav::class.java),
    GravidKravRepository

class PostgresKroniskKravRepository(
    ds: DataSource,
    om: ObjectMapper,
) : SimpleJsonbRepositoryBase<KroniskKrav>("krav_kronisk", ds, om, KroniskKrav::class.java),
    KroniskKravRepository
