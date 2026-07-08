package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Livro de operações financeiras (venda, promoção, pagamento).
 * FK RESTRICT: clientes com operações são arquivados, nunca apagados.
 */
@Entity(
    tableName = "operacoes",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["clienteId"])],
)
data class Operacao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val tipoOperacao: ModoOperacao,
    val valorCentavos: Long,
    val data: Date,
)
