package com.example.caderneta.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Dinheiro é representado em centavos (Long) em todo o domínio e no banco.
 * Conversões para exibição/entrada acontecem exclusivamente na borda da UI,
 * através destas funções.
 */

private val localeBr = Locale("pt", "BR")

/** Formata centavos como moeda brasileira, ex.: 123456 -> "R$ 1.234,56". */
fun Long.centavosParaReais(): String =
    NumberFormat.getCurrencyInstance(localeBr).format(BigDecimal(this).movePointLeft(2))

/** Formata centavos como decimal editável, ex.: 123456 -> "1234,56". */
fun Long.centavosParaTextoDecimal(): String = String.format(localeBr, "%.2f", BigDecimal(this).movePointLeft(2))

/**
 * Interpreta texto decimal (aceita vírgula ou ponto) como centavos.
 * Retorna null para entrada inválida. Ex.: "12,34" -> 1234.
 */
fun String.decimalParaCentavos(): Long? {
    val normalizado = trim().replace(',', '.')
    if (normalizado.isEmpty()) return null
    return try {
        BigDecimal(normalizado).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    } catch (_: NumberFormatException) {
        null
    } catch (_: ArithmeticException) {
        null
    }
}

/** Conversão de legado (Double em reais) para centavos, com arredondamento half-up. */
fun Double.reaisParaCentavos(): Long =
    BigDecimal(toString()).movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
