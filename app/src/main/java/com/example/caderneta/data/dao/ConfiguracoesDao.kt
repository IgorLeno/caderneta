package com.example.caderneta.data.dao

import androidx.room.*
import com.example.caderneta.data.entity.Configuracoes
import kotlinx.coroutines.flow.Flow

@Dao
interface ConfiguracoesDao {
    @Query("SELECT * FROM configuracoes LIMIT 1")
    fun getConfiguracoes(): Flow<Configuracoes?>

    @Query("SELECT * FROM configuracoes WHERE id = 1 LIMIT 1")
    suspend fun getConfiguracoesOnce(): Configuracoes?

    @Query("SELECT COUNT(*) FROM configuracoes")
    suspend fun countConfiguracoes(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfiguracoes(configuracoes: Configuracoes)

    @Upsert
    suspend fun upsertConfiguracoes(configuracoes: Configuracoes)

    @Query("DELETE FROM configuracoes")
    suspend fun deleteAllConfiguracoes()
}
