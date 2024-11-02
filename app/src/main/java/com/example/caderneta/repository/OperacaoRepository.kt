package com.example.caderneta.repository

import com.example.caderneta.data.dao.OperacaoDao
import com.example.caderneta.data.entity.Operacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Date

class OperacaoRepository(val operacaoDao: OperacaoDao) {

    fun getAllOperacoes(): Flow<List<Operacao>> = operacaoDao.getAllOperacoes()

    fun getOperacoesByCliente(clienteId: Long): Flow<List<Operacao>> =
        operacaoDao.getOperacoesByCliente(clienteId)

    fun getOperacoesByDateRange(startDate: Date, endDate: Date): Flow<List<Operacao>> =
        operacaoDao.getOperacoesByDateRange(startDate, endDate)

    suspend fun getOperacaoById(id: Long): Operacao? = withContext(Dispatchers.IO) {
        operacaoDao.getOperacaoById(id)
    }

    suspend fun insertOperacao(operacao: Operacao): Long = withContext(Dispatchers.IO) {
        operacaoDao.insertOperacao(operacao)
    }

    suspend fun updateOperacao(operacao: Operacao) = withContext(Dispatchers.IO) {
        operacaoDao.updateOperacao(operacao)
    }

    suspend fun deleteOperacao(operacao: Operacao) = withContext(Dispatchers.IO) {
        operacaoDao.deleteOperacao(operacao)
    }
}