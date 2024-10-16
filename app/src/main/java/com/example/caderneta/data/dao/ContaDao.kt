package com.example.caderneta.data.dao

import androidx.room.*
import com.example.caderneta.data.entity.Conta
import kotlinx.coroutines.flow.Flow

@Dao
interface ContaDao {


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConta(conta: Conta)

    @Update
    suspend fun updateConta(conta: Conta)

    @Query("UPDATE contas SET saldo = saldo + :valor WHERE clienteId = :clienteId")
    suspend fun atualizarSaldo(clienteId: Long, valor: Double)

    @Query("SELECT * FROM contas WHERE clienteId = :clienteId")
    suspend fun getContaByCliente(clienteId: Long): Conta?

}