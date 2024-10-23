package com.example.caderneta.repository

import com.example.caderneta.data.dao.ContaDao
import com.example.caderneta.data.entity.Conta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ContaRepository(private val contaDao: ContaDao) {

    suspend fun getContaByCliente(clienteId: Long): Conta? = withContext(Dispatchers.IO) {
        contaDao.getContaByCliente(clienteId)
    }

    suspend fun insertConta(conta: Conta) = withContext(Dispatchers.IO) {
        contaDao.insertConta(conta)
    }

    suspend fun updateConta(conta: Conta) = withContext(Dispatchers.IO) {
        contaDao.updateConta(conta)
    }

    suspend fun atualizarSaldo(clienteId: Long, valor: Double) = withContext(Dispatchers.IO) {
        val contaExistente = contaDao.getContaByCliente(clienteId)
        if (contaExistente == null) {
            val novaConta = Conta(clienteId = clienteId, saldo = valor)
            insertConta(novaConta)
        } else {
            val novoSaldo = contaExistente.saldo + valor
            updateConta(contaExistente.copy(saldo = novoSaldo))
        }
    }

    suspend fun processarPagamento(clienteId: Long, valorPagamento: Double): Boolean = withContext(Dispatchers.IO) {
        try {
            val conta = getContaByCliente(clienteId)
            if (conta == null) {
                // Se não existe conta, cria uma nova
                insertConta(Conta(clienteId = clienteId, saldo = 0.0))
                return@withContext true
            }

            if (valorPagamento < 0) {
                return@withContext false
            }

            val novoSaldo = conta.saldo - valorPagamento
            updateConta(conta.copy(saldo = novoSaldo))
            return@withContext true
        } catch (e: Exception) {
            return@withContext false
        }
    }

}