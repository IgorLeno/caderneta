package com.example.caderneta.repository

import android.database.sqlite.SQLiteConstraintException
import com.example.caderneta.data.dao.ClienteDao
import com.example.caderneta.data.entity.Cliente
import kotlinx.coroutines.flow.Flow

class ClienteRepository(
    private val clienteDao: ClienteDao,
) {
    fun getAllClientes(): Flow<List<Cliente>> = clienteDao.getAllClientes()

    suspend fun getClienteById(id: Long): Cliente? = clienteDao.getClienteById(id)

    suspend fun insertCliente(cliente: Cliente): Long = clienteDao.insertCliente(cliente)

    suspend fun updateCliente(cliente: Cliente) {
        clienteDao.updateCliente(cliente)
    }

    suspend fun deleteCliente(cliente: Cliente): Boolean {
        return try {
            clienteDao.deleteCliente(cliente)
            false
        } catch (_: SQLiteConstraintException) {
            clienteDao.updateCliente(cliente.copy(arquivado = true))
            true
        }
    }

    // Novo método para buscar clientes hierarquicamente
    fun getClientesByLocalHierarchy(localId: Long): Flow<List<Cliente>> =
        clienteDao.getClientesByLocalHierarchy(localId)

    // Método original mantido para compatibilidade
    suspend fun getClientesByLocal(localId: Long): List<Cliente> = clienteDao.getClientesByLocal(localId)

    suspend fun buscarClientes(query: String): List<Cliente> = clienteDao.buscarClientes(query)
}
