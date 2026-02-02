package no.nav.helse.fritakagp

import java.math.BigDecimal
import java.math.RoundingMode

fun Number.multiplySafe(other: Number): BigDecimal =
    safeOperation(
        operation = BigDecimal::multiply,
        operandA = this,
        operandB = other
    )

fun Number.divideSafe(other: Number, roundingMode: RoundingMode = RoundingMode.HALF_UP): BigDecimal =
    safeOperation(
        operation = { a, b ->
            a.divide(b, 2, roundingMode)
        },
        operandA = this,
        operandB = other
    )

private fun safeOperation(operation: (BigDecimal, BigDecimal) -> BigDecimal, operandA: Number, operandB: Number): BigDecimal =
    operation(
        operandA.toBigDecimal(),
        operandB.toBigDecimal()
    )

private fun Number.toBigDecimal(): BigDecimal =
    this as? BigDecimal
        ?: this.toDouble().let(::BigDecimal)
