package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Saldo devedor do cliente, em centavos. É um cache materializado do livro de
 * operações: só é escrito dentro das transações do FinanceiroService — nunca
 * recalculado/regravado pela UI. CASCADE é seguro: a conta é derivada, e o
 * cliente só pode ser apagado fisicamente quando não tem histórico.
 */
@Entity(
    tableName = "contas",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class Conta(
    @PrimaryKey val clienteId: Long,
    val saldoCentavos: Long = 0,
)
