package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "produtos")
data class Produto(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val preco: Double,
    val tipo: TipoProduto
)

enum class TipoProduto {
    SALGADO, SUCO
}