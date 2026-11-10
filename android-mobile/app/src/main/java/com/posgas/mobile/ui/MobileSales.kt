package com.posgas.mobile.ui

import java.math.BigDecimal
import java.math.RoundingMode

data class SaleProduct(val id: String, val name: String, val code: String, val price: Double, val available: Boolean)

data class SaleResult(val state: String, val ticket: String, val message: String)

internal fun saleCents(raw: String): Long? =
    raw.trim().replace(',', '.').toBigDecimalOrNull()?.let { value ->
        runCatching { value.movePointRight(2).longValueExact() }.getOrNull()
    }?.takeIf { it in 0..9_007_199_254_740_991L }

internal fun saleCashPayment(
    cashCents: Long,
    receivedC: Map<String, Int>,
    changeC: Map<String, Int>,
    receivedUsd: Map<String, Int>,
    changeUsd: Map<String, Int>,
    exchangeRate: Double?
): Map<String, Any?>? {
    fun Map<String, Int>.amount(): Long = entries.sumOf { (value, count) -> value.toLong() * count }
    val usesDollars = receivedUsd.isNotEmpty() || changeUsd.isNotEmpty()
    if (usesDollars && exchangeRate == null) return null
    val rate = BigDecimal.valueOf(exchangeRate ?: 0.0)
    fun total(cordobas: Long, dollars: Long) = (
        BigDecimal.valueOf(cordobas) + BigDecimal.valueOf(dollars) * rate
        ).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    val received = total(receivedC.amount(), receivedUsd.amount())
    val change = total(changeC.amount(), changeUsd.amount())
    val rounding = received - change - cashCents
    if (rounding !in 0..99) return null
    return mapOf(
        "recibido_denom_c" to receivedC, "cambio_denom_c" to changeC,
        "recibido_denom_usd" to receivedUsd, "cambio_denom_usd" to changeUsd,
        "tasa" to (exchangeRate ?: 0.0), "total_recibido" to received / 100.0,
        "vuelto" to change / 100.0, "redondeo_favor_negocio" to rounding / 100.0
    )
}
