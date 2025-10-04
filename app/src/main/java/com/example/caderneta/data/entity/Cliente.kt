package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "clientes",
    foreignKeys = [
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["localId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["sublocal1Id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["sublocal2Id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["sublocal3Id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        androidx.room.Index(value = ["localId"]),
        androidx.room.Index(value = ["sublocal1Id"]),
        androidx.room.Index(value = ["sublocal2Id"]),
        androidx.room.Index(value = ["sublocal3Id"])
    ]
)

data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val telefone: String?,
    val localId: Long,
    val sublocal1Id: Long? = null,
    val sublocal2Id: Long? = null,
    val sublocal3Id: Long? = null
)