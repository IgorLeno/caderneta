package com.example.caderneta.repository

import com.example.caderneta.data.dao.OperacaoDao
import com.example.caderneta.data.entity.Operacao
import kotlinx.coroutines.flow.Flow
import java.util.Date

class OperacaoRepository(private val operacaoDao: OperacaoDao) {

    fun getAllOperacoes(): Flow<List<Operacao>> = operacaoDao.getAllOperacoes()

    fun getOperacoesByCliente(clienteId: Long): Flow<List<Operacao>> =
        operacaoDao.getOperacoesByCliente(clienteId)

    fun getOperacoesByDateRange(startDate: Date, endDate: Date): Flow<List<Operacao>> =
        operacaoDao.getOperacoesByDateRange(startDate, endDate)

    suspend fun insertOperacao(operacao: Operacao): Long = operacaoDao.insertOperacao(operacao)

    suspend fun updateOperacao(operacao: Operacao) = operacaoDao.updateOperacao(operacao)

    suspend fun deleteOperacao(operacao: Operacao) = operacaoDao.deleteOperacao(operacao)
}