package com.example.caderneta.domain.configuracoes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfiguracoesFormValidatorTest {
    @Test
    fun promocoesDesativadasComCamposPromocionaisVaziosValidaPrecos() {
        val result =
            ConfiguracoesFormValidator.validar(
                ConfiguracoesInput(
                    precoSalgadoVista = "5,00",
                    precoSalgadoPrazo = "6,00",
                    precoSucoVista = "3,00",
                    precoSucoPrazo = "4,00",
                    promocoesAtivadas = false,
                ),
            )

        assertTrue(result is ConfiguracoesFormResult.Valida)
        val configuracoes = (result as ConfiguracoesFormResult.Valida).configuracoes
        assertEquals(500L, configuracoes.precoSalgadoVistaCentavos)
        assertEquals(600L, configuracoes.precoSalgadoPrazoCentavos)
        assertEquals(300L, configuracoes.precoSucoVistaCentavos)
        assertEquals(400L, configuracoes.precoSucoPrazoCentavos)
        assertFalse(configuracoes.promocoesAtivadas)
        assertTrue(configuracoes.isValid())
    }

    @Test
    fun precoObrigatorioVazioRetornaErroDoCampo() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(precoSalgadoVista = ""),
            )

        val erros = (result as ConfiguracoesFormResult.Invalida).erros
        assertEquals("Informe o preço à vista", erros[ConfiguracoesCampo.SALGADO_VISTA])
    }

    @Test
    fun precoZeroRetornaErroVisivel() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(precoSucoPrazo = "0"),
            )

        val erros = (result as ConfiguracoesFormResult.Invalida).erros
        assertEquals("Informe um valor maior que zero", erros[ConfiguracoesCampo.SUCO_PRAZO])
    }

    @Test
    fun textoMonetarioInvalidoRetornaErroVisivel() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(precoSucoVista = "abc"),
            )

        val erros = (result as ConfiguracoesFormResult.Invalida).erros
        assertEquals("Valor inválido", erros[ConfiguracoesCampo.SUCO_VISTA])
    }

    @Test
    fun promocoesAtivadasExigemCamposCompletos() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(
                    promocoesAtivadas = true,
                    promo1Nome = "",
                    promo1Salgados = "",
                    promo1Sucos = "1",
                    promo1Vista = "7,00",
                    promo1Prazo = "8,00",
                    promo2Nome = "Promo 2",
                    promo2Salgados = "2",
                    promo2Sucos = "2",
                    promo2Vista = "9,00",
                    promo2Prazo = "10,00",
                ),
            )

        val erros = (result as ConfiguracoesFormResult.Invalida).erros
        assertEquals("Informe o nome da promoção", erros[ConfiguracoesCampo.PROMO1_NOME])
        assertEquals("Informe a quantidade", erros[ConfiguracoesCampo.PROMO1_SALGADOS])
    }

    @Test
    fun promocoesAtivadasCompletasValidam() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(
                    promocoesAtivadas = true,
                    promo1Nome = "Promo 1",
                    promo1Salgados = "1",
                    promo1Sucos = "1",
                    promo1Vista = "7,00",
                    promo1Prazo = "8,00",
                    promo2Nome = "Promo 2",
                    promo2Salgados = "2",
                    promo2Sucos = "2",
                    promo2Vista = "9,00",
                    promo2Prazo = "10,00",
                ),
            )

        assertTrue(result is ConfiguracoesFormResult.Valida)
        assertTrue((result as ConfiguracoesFormResult.Valida).configuracoes.isValid())
    }

    private fun inputValido() =
        ConfiguracoesInput(
            precoSalgadoVista = "5,00",
            precoSalgadoPrazo = "6,00",
            precoSucoVista = "3,00",
            precoSucoPrazo = "4,00",
            promocoesAtivadas = false,
        )
}
