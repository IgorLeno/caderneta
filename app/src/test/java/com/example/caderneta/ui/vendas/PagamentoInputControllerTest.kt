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
    fun retornaErroParaEntradaInvalida() {
        val result = controller.parseChange(boundClienteId = 42, isProgrammatic = false, text = "-1")

        assertEquals(PagamentoInputController.Result.Erro("Valor não pode ser negativo"), result)
    }
}
