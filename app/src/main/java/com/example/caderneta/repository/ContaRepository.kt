package com.example.caderneta.repository

import com.example.caderneta.data.dao.ContaDao
import com.example.caderneta.data.entity.Conta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ContaRepository(private val contaDao: ContaDao) {

    suspend fun getContaByCliente(clienteId: Long): Conta? {
        return contaDao.getContaByCliente(clienteId)
    }

    suspend fun insertConta(conta: Conta) {
        contaDao.insertConta(conta)
    }

    suspend fun updateConta(conta: Conta) {
        contaDao.updateConta(conta)
    }

    suspend fun atualizarSaldo(clienteId: Long, valor: Double) {
        val contaExistente = contaDao.getContaByCliente(clienteId)
        if (contaExistente == null) {
            val novaConta = Conta(clienteId = clienteId, saldo = valor)
            insertConta(novaConta)
        } else {
            val novoSaldo = contaExistente.saldo + valor
            updateConta(contaExistente.copy(saldo = novoSaldo))
        }
    }

    suspend fun processarPagamento(clienteId: Long, valorPagamento: Double): Boolean {
        try {
            if (valorPagamento <= 0) {
                return false
            }

            val conta = getContaByCliente(clienteId)
            if (conta == null) {
                // Se não existe conta, não é possível processar pagamento
                return false
            }

            val novoSaldo = conta.saldo - valorPagamento
            updateConta(conta.copy(saldo = novoSaldo))
            return true
        } catch (e: Exception) {
            return false
        }
    }

}