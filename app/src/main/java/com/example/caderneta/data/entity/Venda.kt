package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "vendas",
    foreignKeys = [
        ForeignKey(
            entity = Cliente::class,
            parentColumns = ["id"],
            childColumns = ["clienteId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["localId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        androidx.room.Index(value = ["clienteId"]),
        androidx.room.Index(value = ["localId"]),
        androidx.room.Index(value = ["data"]),
        androidx.room.Index(value = ["operacaoId"])
    ]
)

data class Venda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operacaoId: Long,
    val clienteId: Long,
    val localId: Long,
    val data: Date,
    val transacao: String,
    val quantidadeSalgados: Int,
    val quantidadeSucos: Int,
    val isPromocao: Boolean = false,
    val promocaoDetalhes: String? = null,
    val valor: Double
)