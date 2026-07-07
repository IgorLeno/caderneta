package com.example.caderneta.viewmodel

import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.TipoTransacao

/**
 * Estado de edição de operação por cliente na tela de vendas (e na edição de
 * extrato). Imutável: alterações produzem cópias via copy().
 * Valores em centavos — ver util/Dinheiro.kt.
 */
data class ClienteState(
    val clienteId: Long,
    val modoOperacao: ModoOperacao? = null,
    val tipoTransacao: TipoTransacao? = null,
    val quantidadeSalgados: Int = 0,
    val quantidadeSucos: Int = 0,
    val quantidadePromo1: Int = 0,
    val quantidadePromo2: Int = 0,
    val valorTotalCentavos: Long = 0,
) {
    fun isPromocao() = modoOperacao == ModoOperacao.PROMOCAO
}

/** Valor (centavos) de venda normal conforme o tipo de transação. */
fun ClienteState.calcularValorVendaNormal(config: Configuracoes): Long {
    val tipo = tipoTransacao ?: return 0
    return when (tipo) {
        TipoTransacao.A_VISTA ->
            quantidadeSalgados * config.precoSalgadoVistaCentavos +
                quantidadeSucos * config.precoSucoVistaCentavos
        TipoTransacao.A_PRAZO ->
            quantidadeSalgados * config.precoSalgadoPrazoCentavos +
                quantidadeSucos * config.precoSucoPrazoCentavos
    }
}

/** Valor (centavos) das promoções selecionadas. */
fun ClienteState.calcularValorPromocional(config: Configuracoes): Long {
    if (!config.promocoesAtivadas) return 0
    val tipo = tipoTransacao ?: return 0
    return config.calcularValorPromocao(1, quantidadePromo1, tipo) +
        config.calcularValorPromocao(2, quantidadePromo2, tipo)
}

/** Quantidades totais (salgados, sucos), expandindo pacotes promocionais. */
fun ClienteState.calcularQuantidadesTotais(config: Configuracoes): Pair<Int, Int> =
    if (isPromocao()) {
        val quant1 = config.calcularQuantidadesPromocao(1, quantidadePromo1)
        val quant2 = config.calcularQuantidadesPromocao(2, quantidadePromo2)
        Pair(quant1.salgados + quant2.salgados, quant1.sucos + quant2.sucos)
    } else {
        Pair(quantidadeSalgados, quantidadeSucos)
    }

/** Descrição textual dos pacotes promocionais, ex.: "2x Promo 1, 1x Promo 2". */
fun ClienteState.montarPromocaoDetalhes(config: Configuracoes): String {
    val detalhes = mutableListOf<String>()
    if (quantidadePromo1 > 0) detalhes.add("${quantidadePromo1}x ${config.promo1Nome}")
    if (quantidadePromo2 > 0) detalhes.add("${quantidadePromo2}x ${config.promo2Nome}")
    return detalhes.joinToString(", ")
}
