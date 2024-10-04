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
    ]
)
data class Venda(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clienteId: Long,
    val localId: Long,
    val data: Date,
    val total: Double,
    val pago: Boolean,
    val quantidadeSalgados: Int,
    val quantidadeSucos: Int
)