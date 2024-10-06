package com.example.caderneta.data.dao

import androidx.room.*
import com.example.caderneta.data.entity.Configuracoes
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfiguracoesDao {
    @Query("SELECT * FROM configuracoes LIMIT 1")
    fun getConfiguracoes(): Flow<Configuracoes?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfiguracoes(configuracoes: Configuracoes)

    @Update
    suspend fun updateConfiguracoes(configuracoes: Configuracoes)

    @Query("DELETE FROM configuracoes")
    suspend fun deleteAllConfiguracoes()

    @Transaction
    suspend fun resetAndInsertConfiguracoes(configuracoes: Configuracoes) {
        deleteAllConfiguracoes()
        insertConfiguracoes(configuracoes)
    }
}