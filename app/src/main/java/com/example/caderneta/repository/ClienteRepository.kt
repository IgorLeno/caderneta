package com.example.caderneta.repository

import com.example.caderneta.data.dao.ClienteDao
import com.example.caderneta.data.entity.Cliente
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ClienteRepository(private val clienteDao: ClienteDao) {
    fun getAllClientes(): Flow<List<Cliente>> = clienteDao.getAllClientes()

    suspend fun getClienteById(id: Long): Cliente? = withContext(Dispatchers.IO) {
        clienteDao.getClienteById(id)
    }

    suspend fun insertCliente(cliente: Cliente): Long = withContext(Dispatchers.IO) {
        clienteDao.insertCliente(cliente)
    }

    suspend fun updateCliente(cliente: Cliente) = withContext(Dispatchers.IO) {
        clienteDao.updateCliente(cliente)
    }

    suspend fun deleteCliente(cliente: Cliente) = withContext(Dispatchers.IO) {
        clienteDao.deleteCliente(cliente)
    }

    // Novo método para buscar clientes hierarquicamente
    fun getClientesByLocalHierarchy(localId: Long): Flow<List<Cliente>> =
        clienteDao.getClientesByLocalHierarchy(localId)

    // Método original mantido para compatibilidade
    suspend fun getClientesByLocal(localId: Long): List<Cliente> = withContext(Dispatchers.IO) {
        clienteDao.getClientesByLocal(localId)
    }

    suspend fun buscarClientes(query: String): List<Cliente> = withContext(Dispatchers.IO) {
        clienteDao.buscarClientes(query)
    }
}