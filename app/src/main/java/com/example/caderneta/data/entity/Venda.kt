package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Lançamento do extrato: venda (à vista/a prazo, normal ou promocional) ou
 * pagamento de dívida. Registros financeiros são históricos — as FKs usam
 * RESTRICT: clientes/locais com lançamentos são arquivados, nunca apagados.
 *
 * `localId` é nulo para pagamentos registrados sem local selecionado.
 * `valorCentavos` em centavos (Long) — ver util/Dinheiro.kt.
 */
@Entity(
    tableName = "vendas",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["localId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["clienteId"]),
        Index(value = ["localId"]),
        Index(value = ["data"]),
        Index(value = ["operacaoId"]),
    ],
)
data class Venda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operacaoId: Long,
    val clienteId: Long,
    val localId: Long?,
    val data: Date,
    val transacao: TransacaoVenda,
    val quantidadeSalgados: Int,
    val quantidadeSucos: Int,
    val isPromocao: Boolean = false,
    val promocaoDetalhes: String? = null,
    val valorCentavos: Long,
)
