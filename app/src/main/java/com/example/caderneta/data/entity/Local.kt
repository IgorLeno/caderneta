package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Ignore
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * `arquivado`: soft-delete — local some das listas ativas mas o histórico
 * permanece. `isExpanded` é estado de interface (não persistido): quem define
 * é o ViewModel ao emitir a lista.
 */
@Entity(
    tableName = "locais",
    foreignKeys = [
        ForeignKey(
            entity = Local::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["parentId"])],
)
data class Local(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val endereco: String? = null,
    val parentId: Long? = null,
    val level: Int = 0,
    val arquivado: Boolean = false,
    @Ignore val isExpanded: Boolean = false,
) {
    constructor(
        id: Long,
        nome: String,
        endereco: String?,
        parentId: Long?,
        level: Int,
        arquivado: Boolean,
    ) : this(id, nome, endereco, parentId, level, arquivado, false)

    override fun toString(): String = nome
}
