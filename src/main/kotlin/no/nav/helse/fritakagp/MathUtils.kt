package no.nav.helse.fritakagp

import java.math.BigDecimal

fun Double.multipySafe(other: Double): Double =
    BigDecimal(this).multiply(BigDecimal(other)).toDouble()
