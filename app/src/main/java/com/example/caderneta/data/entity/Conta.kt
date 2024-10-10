package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contas")
data class Conta(
    @PrimaryKey val clienteId: Long,
    var saldo: Double = 0.0
)