package com.example.caderneta.repository

import com.example.caderneta.data.dao.ContaDao
import com.example.caderneta.data.entity.Conta
import kotlinx.coroutines.flow.Flow

/**
 * Leitura de saldos. Escrita de saldo é exclusiva do FinanceiroService,
 * dentro de transações — por isso este repository não expõe mutações.
 */
class ContaRepository(
    private val contaDao: ContaDao,
) {
    fun observeAllContas(): Flow<List<Conta>> = contaDao.observeAllContas()

    suspend fun getContaByCliente(clienteId: Long): Conta? = contaDao.getContaByCliente(clienteId)

    suspend fun getSaldoCentavos(clienteId: Long): Long = contaDao.getContaByCliente(clienteId)?.saldoCentavos ?: 0
}
