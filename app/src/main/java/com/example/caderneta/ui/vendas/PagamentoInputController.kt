package com.example.caderneta.ui.vendas

import com.example.caderneta.util.ParseDinheiro
import com.example.caderneta.util.parseDinheiro

class PagamentoInputController {
    fun parseChange(
        boundClienteId: Long?,
        isProgrammatic: Boolean,
        text: String,
    ): Result {
        if (isProgrammatic || boundClienteId == null) return Result.Ignorado
        return when (val valor = text.parseDinheiro()) {
            is ParseDinheiro.Valido -> Result.Valido(boundClienteId, valor.centavos)
            ParseDinheiro.Vazio -> Result.Erro("Informe o valor")
            ParseDinheiro.Invalido -> Result.Erro("Valor inválido")
            ParseDinheiro.Negativo -> Result.Erro("Valor não pode ser negativo")
            ParseDinheiro.MuitoGrande -> Result.Erro("Valor muito grande")
        }
    }

    sealed class Result {
        data object Ignorado : Result()

        data class Valido(
            val clienteId: Long,
            val centavos: Long,
        ) : Result()

        data class Erro(
            val mensagem: String,
        ) : Result()
    }
}
