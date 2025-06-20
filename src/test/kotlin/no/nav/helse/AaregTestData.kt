package no.nav.helse

import no.nav.helsearbeidsgiver.aareg.Periode
import no.nav.helsearbeidsgiver.utils.test.date.april
import no.nav.helsearbeidsgiver.utils.test.date.august
import no.nav.helsearbeidsgiver.utils.test.date.desember
import no.nav.helsearbeidsgiver.utils.test.date.februar
import no.nav.helsearbeidsgiver.utils.test.date.januar
import no.nav.helsearbeidsgiver.utils.test.date.juni
import no.nav.helsearbeidsgiver.utils.test.date.mai
import no.nav.helsearbeidsgiver.utils.test.date.mars
import no.nav.helsearbeidsgiver.utils.test.date.november
import no.nav.helsearbeidsgiver.utils.test.date.september
import java.time.LocalDate

object AaregTestData {
    val evigAnsettelsesperiode = setOf(
        Periode(
            LocalDate.MIN,
            LocalDate.MAX
        )
    )
    val avsluttetAnsettelsesperiode = setOf(
        Periode(
            LocalDate.MIN,
            5.februar(2021)
        )
    )

    val paagaaendeAnsettelsesperiode = setOf(
        Periode(
            5.februar(2021),
            null
        )
    )
    val ansettelsesperioderMedSluttDato = setOf(
        Periode(
            1.juni(2004),
            30.juni(2004)
        ),
        Periode(
            1.september(2004),
            30.september(2004)
        ),
        Periode(
            1.januar(2005),
            28.februar(2005)
        ),
        Periode(
            6.september(2005),
            31.desember(2007)
        ),
        Periode(
            16.juni(2008),
            3.august(2008)
        ),
        Periode(
            5.mars(2009),
            30.august(2010)
        ),
        Periode(
            26.november(2010),
            4.september(2011)
        ),
        Periode(
            5.september(2011),
            30.mars(2013)
        ),
        Periode(
            31.mars(2013),
            1.januar(2014)
        ),
        Periode(
            31.mars(2013),
            31.mars(2013)
        ),
        Periode(
            24.februar(2014),
            24.februar(2014)
        ),
        Periode(
            28.mars(2014),
            31.mai(2014)
        ),
        Periode(
            1.juni(2014),
            30.april(2022)
        ),
        Periode(
            1.juni(2014),
            31.desember(2014)
        ),
        Periode(
            1.mai(2022),
            null
        )
    )
}
