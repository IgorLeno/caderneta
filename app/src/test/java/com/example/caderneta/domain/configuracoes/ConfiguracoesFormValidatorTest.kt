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
    fun promocoesDesativadasPreservamCamposPromocionaisPreenchidos() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(
                    promocoesAtivadas = false,
                    promo1Nome = "Combo antigo",
                    promo1Salgados = "2",
                    promo1Sucos = "1",
                    promo1Vista = "9,00",
                    promo1Prazo = "10,00",
                    promo2Nome = "Combo maior",
                    promo2Salgados = "3",
                    promo2Sucos = "2",
                    promo2Vista = "14,00",
                    promo2Prazo = "15,00",
                ),
            )

        assertTrue(result is ConfiguracoesFormResult.Valida)
        val configuracoes = (result as ConfiguracoesFormResult.Valida).configuracoes
        assertFalse(configuracoes.promocoesAtivadas)
        assertEquals("Combo antigo", configuracoes.promo1Nome)
        assertEquals(2, configuracoes.promo1Salgados)
        assertEquals(1, configuracoes.promo1Sucos)
        assertEquals(900L, configuracoes.promo1VistaCentavos)
        assertEquals(1000L, configuracoes.promo1PrazoCentavos)
        assertEquals("Combo maior", configuracoes.promo2Nome)
        assertEquals(3, configuracoes.promo2Salgados)
        assertEquals(2, configuracoes.promo2Sucos)
        assertEquals(1400L, configuracoes.promo2VistaCentavos)
        assertEquals(1500L, configuracoes.promo2PrazoCentavos)
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

    @Test
    fun promocoesAtivadasAceitamPromo2Vazia() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(
                    promocoesAtivadas = true,
                    promo1Nome = "Promo 1",
                    promo1Salgados = "1",
                    promo1Sucos = "0",
                    promo1Vista = "7,00",
                    promo1Prazo = "8,00",
                    promo2Nome = "",
                    promo2Salgados = "0",
                    promo2Sucos = "0",
                    promo2Vista = "0",
                    promo2Prazo = "0",
                ),
            )

        assertTrue(result is ConfiguracoesFormResult.Valida)
        val configuracoes = (result as ConfiguracoesFormResult.Valida).configuracoes
        assertTrue(configuracoes.promocoesAtivadas)
        assertEquals("", configuracoes.promo2Nome)
        assertEquals(0, configuracoes.promo2Salgados)
        assertEquals(0, configuracoes.promo2Sucos)
        assertEquals(0L, configuracoes.promo2VistaCentavos)
        assertEquals(0L, configuracoes.promo2PrazoCentavos)
    }

    @Test
    fun promocoesAtivadasExigemAoMenosUmItemNaPromo1() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(
                    promocoesAtivadas = true,
                    promo1Nome = "Promo 1",
                    promo1Salgados = "0",
                    promo1Sucos = "0",
                    promo1Vista = "7,00",
                    promo1Prazo = "8,00",
                ),
            )

        val erros = (result as ConfiguracoesFormResult.Invalida).erros
        assertEquals("Informe ao menos um item", erros[ConfiguracoesCampo.PROMO1_SALGADOS])
    }

    @Test
    fun promo2ParcialmentePreenchidaExigeCamposCompletos() {
        val result =
            ConfiguracoesFormValidator.validar(
                inputValido().copy(
                    promocoesAtivadas = true,
                    promo1Nome = "Promo 1",
                    promo1Salgados = "1",
                    promo1Sucos = "0",
                    promo1Vista = "7,00",
                    promo1Prazo = "8,00",
                    promo2Nome = "Promo 2",
                    promo2Salgados = "",
                    promo2Sucos = "1",
                    promo2Vista = "",
                    promo2Prazo = "10,00",
                ),
            )

        val erros = (result as ConfiguracoesFormResult.Invalida).erros
        assertEquals("Informe a quantidade", erros[ConfiguracoesCampo.PROMO2_SALGADOS])
        assertEquals("Informe o valor à vista", erros[ConfiguracoesCampo.PROMO2_VISTA])
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
