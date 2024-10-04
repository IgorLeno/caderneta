package com.example.caderneta.data.dao

import androidx.room.*
import com.example.caderneta.data.entity.Cliente
import kotlinx.coroutines.flow.Flow

@Dao
interface ClienteDao {
    @Query("SELECT * FROM clientes")
    fun getAllClientes(): Flow<List<Cliente>>

    @Query("SELECT * FROM clientes WHERE id = :id")
    suspend fun getClienteById(id: Long): Cliente?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCliente(cliente: Cliente): Long

    @Update
    suspend fun updateCliente(cliente: Cliente)

    @Delete
    suspend fun deleteCliente(cliente: Cliente)

    @Query("SELECT * FROM clientes WHERE localId = :localId")
    fun getClientesByLocal(localId: Long): List<Cliente>

    @Query("SELECT * FROM clientes WHERE nome LIKE :query")
    suspend fun buscarClientes(query: String): List<Cliente>
}