package com.example.caderneta.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "locais")
data class Local(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nome: String,
    val endereco: String?= null,
    val parentId: Long? = null,
    val level: Int = 0,
    var isExpanded: Boolean = false
) {
    override fun toString(): String = nome
}