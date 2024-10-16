package com.example.caderneta.repository

import com.example.caderneta.data.dao.ContaDao
import com.example.caderneta.data.entity.Conta
import kotlinx.coroutines.flow.Flow

class ContaRepository(private val contaDao: ContaDao) {



    suspend fun getContaByCliente(clienteId: Long): Conta? {
        return contaDao.getContaByCliente(clienteId)
    }


    suspend fun insertConta(conta: Conta) = contaDao.insertConta(conta)

    suspend fun updateConta(conta: Conta) = contaDao.updateConta(conta)

    suspend fun atualizarSaldo(clienteId: Long, valor: Double) =
        contaDao.atualizarSaldo(clienteId, valor)


}