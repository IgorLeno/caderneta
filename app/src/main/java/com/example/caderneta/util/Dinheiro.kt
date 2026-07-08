package com.example.caderneta.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/**
 * Dinheiro é representado em centavos (Long) em todo o domínio e no banco.
 * Conversões para exibição/entrada acontecem exclusivamente na borda da UI.
 */

private val localeBr = Locale.forLanguageTag("pt-BR")

sealed class ParseDinheiro {
    data class Valido(
        val centavos: Long,
    ) : ParseDinheiro()

    data object Vazio : ParseDinheiro()

    data object Invalido : ParseDinheiro()

    data object Negativo : ParseDinheiro()

    data object MuitoGrande : ParseDinheiro()
}

/** Formata centavos como moeda brasileira, ex.: 123456 -> "R$ 1.234,56". */
fun Long.centavosParaReais(): String =
    NumberFormat.getCurrencyInstance(localeBr).format(BigDecimal(this).movePointLeft(2))

/** Formata centavos como decimal editável, ex.: 123456 -> "1234,56". */
fun Long.centavosParaTextoDecimal(): String = String.format(localeBr, "%.2f", BigDecimal(this).movePointLeft(2))

/**
 * Interpreta texto monetário em centavos.
 *
 * Aceita vírgula ou ponto como separador decimal. Quando ambos aparecem, o
 * último separador é decimal e o outro é milhar. Separador repetido é tratado
 * como milhar, então "1.234.567" vira 123456700 centavos.
 */
@Suppress("ReturnCount")
fun String.parseDinheiro(): ParseDinheiro {
    val semMoeda =
        trim()
            .replace("R$", "", ignoreCase = true)
            .replace("\u00A0", "")
            .replace(" ", "")

    if (semMoeda.isEmpty()) return ParseDinheiro.Vazio
    if (semMoeda.startsWith("-")) return ParseDinheiro.Negativo

    val normalizado = normalizarDecimal(semMoeda) ?: return ParseDinheiro.Invalido

    return try {
        val decimal = BigDecimal(normalizado)
        if (decimal.signum() < 0) return ParseDinheiro.Negativo

        val centavos =
            decimal
                .movePointRight(2)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()

        ParseDinheiro.Valido(centavos)
    } catch (_: NumberFormatException) {
        ParseDinheiro.Invalido
    } catch (_: ArithmeticException) {
        ParseDinheiro.MuitoGrande
    }
}

/** Wrapper compatível: retorna null para qualquer entrada não monetária válida. */
fun String.decimalParaCentavos(): Long? = (parseDinheiro() as? ParseDinheiro.Valido)?.centavos

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun normalizarDecimal(texto: String): String? {
    if (!texto.all(::isCaracterMonetario)) return null
    val quantidadeSinais = texto.count { it == '+' }
    if (quantidadeSinais > 1) return null
    if (quantidadeSinais == 1 && !texto.startsWith('+')) return null

    val valor = texto.removePrefix("+")
    if (valor.isEmpty()) return null

    val virgulas = valor.count { it == ',' }
    val pontos = valor.count { it == '.' }
    if (virgulas == 0 && pontos == 0) return valor.takeIf { it.all(Char::isDigit) }

    if (virgulas > 0 && pontos > 0) {
        val decimal = if (valor.lastIndexOf(',') > valor.lastIndexOf('.')) ',' else '.'
        val milhar = if (decimal == ',') '.' else ','
        return valor.replace(milhar.toString(), "").replace(decimal, '.')
    }

    val separador = if (virgulas > 0) ',' else '.'
    val repetido = if (separador == ',') virgulas > 1 else pontos > 1
    return if (repetido) {
        valor.replace(separador.toString(), "").takeIf { it.all(Char::isDigit) }
    } else {
        valor.replace(separador, '.')
    }
}

private fun isCaracterMonetario(char: Char): Boolean =
    char.isDigit() ||
        char == ',' ||
        char == '.' ||
        char == '+'
