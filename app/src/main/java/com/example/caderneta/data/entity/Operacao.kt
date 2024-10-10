package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "operacoes")
data class Operacao(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val tipoOperacao: String,
    val valor: Double,
    val data: Date
)