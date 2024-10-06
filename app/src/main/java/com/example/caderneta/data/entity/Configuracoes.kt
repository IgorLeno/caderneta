package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "configuracoes")
data class Configuracoes(
    @PrimaryKey val id: Int = 1,
    val precoSalgadoVista: Double,
    val precoSalgadoPrazo: Double,
    val precoSucoVista: Double,
    val precoSucoPrazo: Double,
    val promocoesAtivadas: Boolean,
    val promo1Nome: String,
    val promo1Salgados: Int,
    val promo1Sucos: Int,
    val promo1Vista: Double,
    val promo1Prazo: Double,
    val promo2Nome: String,
    val promo2Salgados: Int,
    val promo2Sucos: Int,
    val promo2Vista: Double,
    val promo2Prazo: Double
) {
    fun isValid(): Boolean {
        return precoSalgadoVista > 0 && precoSalgadoPrazo > 0 &&
                precoSucoVista > 0 && precoSucoPrazo > 0 &&
                promo1Salgados >= 0 && promo1Sucos >= 0 &&
                promo1Vista >= 0 && promo1Prazo >= 0 &&
                promo2Salgados >= 0 && promo2Sucos >= 0 &&
                promo2Vista >= 0 && promo2Prazo >= 0
    }
}