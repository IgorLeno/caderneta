package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.caderneta.util.centavosParaReais

/**
 * Preços em centavos (Long) — ver util/Dinheiro.kt.
 */
@Entity(tableName = "configuracoes")
data class Configuracoes(
    @PrimaryKey val id: Int = 1,
    // Preços padrão
    val precoSalgadoVistaCentavos: Long,
    val precoSalgadoPrazoCentavos: Long,
    val precoSucoVistaCentavos: Long,
    val precoSucoPrazoCentavos: Long,
    // Flag de ativação de promoções
    val promocoesAtivadas: Boolean,
    // Promoção 1
    val promo1Nome: String,
    val promo1Salgados: Int,
    val promo1Sucos: Int,
    val promo1VistaCentavos: Long,
    val promo1PrazoCentavos: Long,
    // Promoção 2
    val promo2Nome: String,
    val promo2Salgados: Int,
    val promo2Sucos: Int,
    val promo2VistaCentavos: Long,
    val promo2PrazoCentavos: Long,
) {
    fun isValid(): Boolean {
        val basicValuesValid =
            precoSalgadoVistaCentavos > 0 &&
                precoSalgadoPrazoCentavos > 0 &&
                precoSucoVistaCentavos > 0 &&
                precoSucoPrazoCentavos > 0

        val promocoesValid =
            if (promocoesAtivadas) {
                promo1Salgados >= 0 &&
                    promo1Sucos >= 0 &&
                    promo1VistaCentavos > 0 &&
                    promo1PrazoCentavos > 0 &&
                    promo2Salgados >= 0 &&
                    promo2Sucos >= 0 &&
                    promo2VistaCentavos > 0 &&
                    promo2PrazoCentavos > 0 &&
                    promo1Nome.isNotBlank() &&
                    promo2Nome.isNotBlank()
            } else {
                true
            }

        return basicValuesValid && promocoesValid
    }

    /** Valor total (centavos) de uma promoção para a quantidade e tipo de transação. */
    fun calcularValorPromocao(
        numeroPromocao: Int,
        quantidade: Int,
        tipoTransacao: TipoTransacao,
    ): Long {
        if (!promocoesAtivadas || quantidade <= 0) return 0

        return when (numeroPromocao) {
            1 -> quantidade * if (tipoTransacao == TipoTransacao.A_VISTA) promo1VistaCentavos else promo1PrazoCentavos
            2 -> quantidade * if (tipoTransacao == TipoTransacao.A_VISTA) promo2VistaCentavos else promo2PrazoCentavos
            else -> 0
        }
    }

    /** Quantidades totais de itens para uma promoção. */
    fun calcularQuantidadesPromocao(
        numeroPromocao: Int,
        quantidade: Int,
    ): PromoQuantidades {
        if (!promocoesAtivadas || quantidade <= 0) {
            return PromoQuantidades(0, 0)
        }

        return when (numeroPromocao) {
            1 ->
                PromoQuantidades(
                    salgados = promo1Salgados * quantidade,
                    sucos = promo1Sucos * quantidade,
                )
            2 ->
                PromoQuantidades(
                    salgados = promo2Salgados * quantidade,
                    sucos = promo2Sucos * quantidade,
                )
            else -> PromoQuantidades(0, 0)
        }
    }

    fun isPromocaoValida(numeroPromocao: Int): Boolean {
        if (!promocoesAtivadas) return false

        return when (numeroPromocao) {
            1 -> promo1Salgados > 0 || promo1Sucos > 0
            2 -> promo2Salgados > 0 || promo2Sucos > 0
            else -> false
        }
    }

    fun getNomePromocao(numeroPromocao: Int): String {
        if (!promocoesAtivadas) return ""

        return when (numeroPromocao) {
            1 -> promo1Nome
            2 -> promo2Nome
            else -> ""
        }
    }

    fun getDescricaoPromocao(numeroPromocao: Int): String {
        if (!promocoesAtivadas) return ""

        return when (numeroPromocao) {
            1 -> formatarDescricaoPromocao(promo1Salgados, promo1Sucos, promo1VistaCentavos, promo1PrazoCentavos)
            2 -> formatarDescricaoPromocao(promo2Salgados, promo2Sucos, promo2VistaCentavos, promo2PrazoCentavos)
            else -> ""
        }
    }

    private fun formatarDescricaoPromocao(
        salgados: Int,
        sucos: Int,
        valorVistaCentavos: Long,
        valorPrazoCentavos: Long,
    ): String {
        val itens = mutableListOf<String>()
        if (salgados > 0) itens.add("$salgados salgado${if (salgados > 1) "s" else ""}")
        if (sucos > 0) itens.add("$sucos suco${if (sucos > 1) "s" else ""}")

        return "${itens.joinToString(" + ")}\n" +
            "À vista: ${valorVistaCentavos.centavosParaReais()} | A prazo: ${valorPrazoCentavos.centavosParaReais()}"
    }
}

// Classe auxiliar para retornar quantidades calculadas
data class PromoQuantidades(
    val salgados: Int,
    val sucos: Int,
)
