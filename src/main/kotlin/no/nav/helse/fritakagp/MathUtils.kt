package no.nav.helse.fritakagp

import java.math.BigDecimal

fun Double.multiplySafe(other: Double): Double =
    BigDecimal(this).multiply(BigDecimal(other)).toDouble()
