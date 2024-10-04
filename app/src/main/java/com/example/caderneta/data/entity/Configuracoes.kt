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
    val quantidadeSalgadosPromocao1: Int,
    val quantidadeSucosPromocao1: Int,
    val valorPromocao1: Double,
    val quantidadeSalgadosPromocao2: Int,
    val quantidadeSucosPromocao2: Int,
    val valorPromocao2: Double
)