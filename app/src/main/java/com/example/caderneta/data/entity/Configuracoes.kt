package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// Atualizações na entidade Configuracoes.kt
@Entity(tableName = "configuracoes")
data class Configuracoes(
    @PrimaryKey val id: Int = 1,
    // Preços padrão
    val precoSalgadoVista: Double,
    val precoSalgadoPrazo: Double,
    val precoSucoVista: Double,
    val precoSucoPrazo: Double,

    // Flag de ativação de promoções
    val promocoesAtivadas: Boolean,

    // Promoção 1
    val promo1Nome: String,
    val promo1Salgados: Int,
    val promo1Sucos: Int,
    val promo1Vista: Double,
    val promo1Prazo: Double,

    // Promoção 2
    val promo2Nome: String,
    val promo2Salgados: Int,
    val promo2Sucos: Int,
    val promo2Vista: Double,
    val promo2Prazo: Double
) {
    fun isValid(): Boolean {
        val basicValuesValid = precoSalgadoVista > 0 && precoSalgadoPrazo > 0 &&
                precoSucoVista > 0 && precoSucoPrazo > 0

        val promocoesValid = if (promocoesAtivadas) {
            // Validação específica para promoções quando ativadas
            promo1Salgados >= 0 && promo1Sucos >= 0 &&
                    promo1Vista > 0 && promo1Prazo > 0 &&
                    promo2Salgados >= 0 && promo2Sucos >= 0 &&
                    promo2Vista > 0 && promo2Prazo > 0 &&
                    promo1Nome.isNotBlank() && promo2Nome.isNotBlank()
        } else true

        return basicValuesValid && promocoesValid
    }

    // Calcula o valor total de uma promoção com base na quantidade e tipo de transação

    fun calcularValorPromocao(
        numeroPromocao: Int,
        quantidade: Int,
        tipoTransacao: TipoTransacao
    ): Double {
        if (!promocoesAtivadas || quantidade <= 0) return 0.0

        return when (numeroPromocao) {
            1 -> quantidade * if (tipoTransacao == TipoTransacao.A_VISTA) promo1Vista else promo1Prazo
            2 -> quantidade * if (tipoTransacao == TipoTransacao.A_VISTA) promo2Vista else promo2Prazo
            else -> 0.0
        }
    }

    // Calcula a quantidade total de itens para uma promoção

    fun calcularQuantidadesPromocao(
        numeroPromocao: Int,
        quantidade: Int
    ): PromoQuantidades {
        if (!promocoesAtivadas || quantidade <= 0) {
            return PromoQuantidades(0, 0)
        }

        return when (numeroPromocao) {
            1 -> PromoQuantidades(
                salgados = promo1Salgados * quantidade,
                sucos = promo1Sucos * quantidade
            )
            2 -> PromoQuantidades(
                salgados = promo2Salgados * quantidade,
                sucos = promo2Sucos * quantidade
            )
            else -> PromoQuantidades(0, 0)
        }
    }

    // Verifica se uma promoção está configurada corretamente

    fun isPromocaoValida(numeroPromocao: Int): Boolean {
        if (!promocoesAtivadas) return false

        return when (numeroPromocao) {
            1 -> promo1Salgados > 0 || promo1Sucos > 0
            2 -> promo2Salgados > 0 || promo2Sucos > 0
            else -> false
        }
    }

    // Obtém o nome da promoção formatado
    fun getNomePromocao(numeroPromocao: Int): String {
        if (!promocoesAtivadas) return ""

        return when (numeroPromocao) {
            1 -> promo1Nome
            2 -> promo2Nome
            else -> ""
        }
    }

    // Obtém a descrição detalhada da promoção
    fun getDescricaoPromocao(numeroPromocao: Int): String {
        if (!promocoesAtivadas) return ""

        return when (numeroPromocao) {
            1 -> formatarDescricaoPromocao(promo1Salgados, promo1Sucos, promo1Vista, promo1Prazo)
            2 -> formatarDescricaoPromocao(promo2Salgados, promo2Sucos, promo2Vista, promo2Prazo)
            else -> ""
        }
    }

    private fun formatarDescricaoPromocao(
        salgados: Int,
        sucos: Int,
        valorVista: Double,
        valorPrazo: Double
    ): String {
        val itens = mutableListOf<String>()
        if (salgados > 0) itens.add("$salgados salgado${if (salgados > 1) "s" else ""}")
        if (sucos > 0) itens.add("$sucos suco${if (sucos > 1) "s" else ""}")

        return "${itens.joinToString(" + ")}\n" +
                "À vista: R$ %.2f | A prazo: R$ %.2f".format(valorVista, valorPrazo)
    }
}

// Classe auxiliar para retornar quantidades calculadas
data class PromoQuantidades(
    val salgados: Int,
    val sucos: Int
)