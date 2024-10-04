package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "itens_venda",
    foreignKeys = [
        ForeignKey(
            entity = Venda::class,
            parentColumns = ["id"],
            childColumns = ["vendaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Produto::class,
            parentColumns = ["id"],
            childColumns = ["produtoId"],
            onDelete = ForeignKey.RESTRICT
        )
    ]
)
data class ItemVenda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendaId: Long,
    val produtoId: Long,
    val quantidade: Int,
    val precoUnitario: Double
)