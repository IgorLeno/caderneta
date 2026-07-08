package com.example.caderneta.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DinheiroTest {
    @Test
    fun parseAceitaVirgulaEPontoDecimal() {
        assertEquals(ParseDinheiro.Valido(1234), "12,34".parseDinheiro())
        assertEquals(ParseDinheiro.Valido(1234), "12.34".parseDinheiro())
    }

    @Test
    fun parseAceitaMilharComDecimal() {
        assertEquals(ParseDinheiro.Valido(123456), "1.234,56".parseDinheiro())
        assertEquals(ParseDinheiro.Valido(123456), "1,234.56".parseDinheiro())
    }

    @Test
    fun parseTrataSeparadorRepetidoComoMilhar() {
        assertEquals(ParseDinheiro.Valido(123456700), "1.234.567".parseDinheiro())
    }

    @Test
    fun parseClassificaEntradasInvalidas() {
        assertEquals(ParseDinheiro.Vazio, "".parseDinheiro())
        assertEquals(ParseDinheiro.Vazio, " R$  ".parseDinheiro())
        assertEquals(ParseDinheiro.Invalido, "abc".parseDinheiro())
        assertEquals(ParseDinheiro.Negativo, "-1,00".parseDinheiro())
    }

    @Test
    fun parseArredondaHalfUp() {
        assertEquals(ParseDinheiro.Valido(123), "1,234".parseDinheiro())
        assertEquals(ParseDinheiro.Valido(124), "1,235".parseDinheiro())
    }

    @Test
    fun parseDetectaOverflow() {
        assertEquals(ParseDinheiro.MuitoGrande, "92233720368547758,08".parseDinheiro())
    }

    @Test
    fun wrapperRetornaCentavosOuNull() {
        assertEquals(1234L, "12,34".decimalParaCentavos())
        assertEquals(null, "abc".decimalParaCentavos())
    }
}
