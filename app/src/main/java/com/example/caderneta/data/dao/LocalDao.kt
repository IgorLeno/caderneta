package com.example.caderneta.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.caderneta.data.entity.Local
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDao {
    @Query("SELECT * FROM locais")
    fun getAllLocais(): Flow<List<Local>>

    @Query("SELECT * FROM locais")
    suspend fun getAllLocaisList(): List<Local>

    @Query("SELECT * FROM locais WHERE id = :id")
    suspend fun getLocalById(id: Long): Local?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocal(local: Local): Long

    @Update
    suspend fun updateLocal(local: Local)

    @Query("UPDATE locais SET arquivado = 1 WHERE id IN (:ids)")
    suspend fun arquivarLocais(ids: List<Long>)

    @Delete
    suspend fun deleteLocal(local: Local)

    @Query("SELECT * FROM locais WHERE nome LIKE :query")
    suspend fun buscarLocais(query: String): List<Local>
}
