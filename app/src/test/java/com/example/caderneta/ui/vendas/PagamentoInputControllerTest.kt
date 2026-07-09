package com.example.caderneta.ui.vendas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PagamentoInputControllerTest {
    private val controller = PagamentoInputController()

    @Test
    fun ignoraAlteracaoProgramatica() {
        val result = controller.parseChange(boundClienteId = 1, isProgrammatic = true, text = "10,00")

        assertTrue(result is PagamentoInputController.Result.Ignorado)
    }

    @Test
    fun ignoraHolderSemClienteVinculado() {
        val result = controller.parseChange(boundClienteId = null, isProgrammatic = false, text = "10,00")

        assertTrue(result is PagamentoInputController.Result.Ignorado)
    }

    @Test
    fun retornaClienteVinculadoAtualNoValorValido() {
        val result = controller.parseChange(boundClienteId = 42, isProgrammatic = false, text = "12,34")

        assertEquals(PagamentoInputController.Result.Valido(42, 1234), result)
    }

    @Test
    fun retornaErroParaVazio() {
        val result = controller.parseChange(boundClienteId = 42, isProgrammatic = false, text = "")

        assertEquals(PagamentoInputController.Result.Erro("Informe o valor"), result)
    }

    @Test
    fun retornaErroParaNegativo() {
        val result = controller.parseChange(boundClienteId = 42, isProgrammatic = false, text = "-1")

        assertEquals(PagamentoInputController.Result.Erro("Valor não pode ser negativo"), result)
    }

    @Test
    fun retornaErroParaZeroInteiro() {
        val result = controller.parseChange(boundClienteId = 42, isProgrammatic = false, text = "0")

        assertEquals(PagamentoInputController.Result.Erro("Informe um valor maior que zero"), result)
    }

    @Test
    fun retornaErroParaZeroDecimalComVirgula() {
        val result = controller.parseChange(boundClienteId = 42, isProgrammatic = false, text = "0,00")

        assertEquals(PagamentoInputController.Result.Erro("Informe um valor maior que zero"), result)
    }

    @Test
    fun retornaErroParaZeroDecimalComPonto() {
        val result = controller.parseChange(boundClienteId = 42, isProgrammatic = false, text = "0.00")

        assertEquals(PagamentoInputController.Result.Erro("Informe um valor maior que zero"), result)
    }
}
