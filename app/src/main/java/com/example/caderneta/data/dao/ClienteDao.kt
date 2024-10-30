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

    // Nova query para buscar clientes por local hierarquicamente
    @Query("""
        WITH RECURSIVE local_hierarchy AS (
            SELECT id, parentId
            FROM locais
            WHERE id = :localId
            UNION ALL
            SELECT l.id, l.parentId
            FROM locais l
            INNER JOIN local_hierarchy lh ON l.parentId = lh.id
        )
        SELECT DISTINCT c.*
        FROM clientes c
        WHERE c.localId IN (SELECT id FROM local_hierarchy)
        OR c.sublocal1Id IN (SELECT id FROM local_hierarchy)
        OR c.sublocal2Id IN (SELECT id FROM local_hierarchy)
        OR c.sublocal3Id IN (SELECT id FROM local_hierarchy)
    """)
    fun getClientesByLocalHierarchy(localId: Long): Flow<List<Cliente>>

    // Mantém a query original para casos específicos
    @Query("SELECT * FROM clientes WHERE localId = :localId")
    fun getClientesByLocal(localId: Long): List<Cliente>

    @Query("SELECT * FROM clientes WHERE nome LIKE :query")
    suspend fun buscarClientes(query: String): List<Cliente>
}