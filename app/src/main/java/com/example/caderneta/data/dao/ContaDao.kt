package com.example.caderneta.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.caderneta.data.entity.Conta
import kotlinx.coroutines.flow.Flow

@Dao
interface ContaDao {
    @Query("SELECT * FROM contas")
    fun observeAllContas(): Flow<List<Conta>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConta(conta: Conta)

    @Update
    suspend fun updateConta(conta: Conta)

    @Query("UPDATE contas SET saldoCentavos = saldoCentavos + :valorCentavos WHERE clienteId = :clienteId")
    suspend fun atualizarSaldo(
        clienteId: Long,
        valorCentavos: Long,
    )

    @Query("SELECT * FROM contas WHERE clienteId = :clienteId")
    suspend fun getContaByCliente(clienteId: Long): Conta?
}
