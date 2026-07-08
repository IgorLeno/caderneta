package com.example.caderneta.viewmodel

sealed class UiEvento {
    data class Erro(
        val mensagem: String,
    ) : UiEvento()

    data class Sucesso(
        val mensagem: String,
    ) : UiEvento()

    data class ConfirmarRestauracao(
        val resumo: String,
    ) : UiEvento()
}
