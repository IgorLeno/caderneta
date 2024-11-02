package com.example.caderneta.data.dao

import androidx.room.*
import com.example.caderneta.data.entity.Operacao
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface OperacaoDao {
    @Query("SELECT * FROM operacoes")
    fun getAllOperacoes(): Flow<List<Operacao>>

    @Query("SELECT * FROM operacoes WHERE id = :id")
    suspend fun getOperacaoById(id: Long): Operacao?

    @Query("SELECT * FROM operacoes WHERE clienteId = :clienteId")
    fun getOperacoesByCliente(clienteId: Long): Flow<List<Operacao>>

    @Query("SELECT * FROM operacoes WHERE data BETWEEN :startDate AND :endDate")
    fun getOperacoesByDateRange(startDate: Date, endDate: Date): Flow<List<Operacao>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOperacao(operacao: Operacao): Long

    @Update
    suspend fun updateOperacao(operacao: Operacao)

    @Delete
    suspend fun deleteOperacao(operacao: Operacao)
}