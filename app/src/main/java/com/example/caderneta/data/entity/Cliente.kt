package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `arquivado`: soft-delete — cliente some das listas ativas mas o histórico
 * financeiro permanece. FK do local principal é RESTRICT (local com clientes
 * não pode ser apagado fisicamente).
 */
@Entity(
    tableName = "clientes",
    foreignKeys = [
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["localId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["sublocal1Id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["sublocal2Id"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["sublocal3Id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["localId"]),
        Index(value = ["sublocal1Id"]),
        Index(value = ["sublocal2Id"]),
        Index(value = ["sublocal3Id"]),
    ],
)
data class Cliente(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val telefone: String?,
    val localId: Long,
    val sublocal1Id: Long? = null,
    val sublocal2Id: Long? = null,
    val sublocal3Id: Long? = null,
    val arquivado: Boolean = false,
    val fotoNome: String? = null,
)
