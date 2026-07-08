package com.example.caderneta.repository

import com.example.caderneta.data.dao.VendaDao
import com.example.caderneta.data.entity.Venda
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Leitura de lançamentos. Escrita (inserir/editar/excluir) é exclusiva do
 * FinanceiroService, dentro de transações.
 */
class VendaRepository(
    private val vendaDao: VendaDao,
) {
    fun getAllVendas(): Flow<List<Venda>> = vendaDao.getAllVendas()

    suspend fun getVendaById(id: Long): Venda? = vendaDao.getVendaById(id)

    fun getVendasByCliente(clienteId: Long): Flow<List<Venda>> = vendaDao.getVendasByCliente(clienteId)

    fun getVendasByLocal(localId: Long): Flow<List<Venda>> = vendaDao.getVendasByLocal(localId)

    fun getVendasByDateRange(
        startDate: Date,
        endDate: Date,
    ): Flow<List<Venda>> = vendaDao.getVendasByDateRange(startDate, endDate)

    fun getVendasByClienteAndDateRange(
        clienteId: Long,
        startDate: Date,
        endDate: Date,
    ): Flow<List<Venda>> = vendaDao.getVendasByClienteAndDateRange(clienteId, startDate, endDate)

    fun getTotalVendasByDateRange(
        startDate: Date,
        endDate: Date,
    ): Flow<Long?> = vendaDao.getTotalVendasByDateRange(startDate, endDate)

    fun getTotalVendasByClienteAndDateRange(
        clienteId: Long,
        startDate: Date,
        endDate: Date,
    ): Flow<Long?> = vendaDao.getTotalVendasByClienteAndDateRange(clienteId, startDate, endDate)

    fun getTotalSalgadosByDateRange(
        startDate: Date,
        endDate: Date,
    ): Flow<Int?> = vendaDao.getTotalSalgadosByDateRange(startDate, endDate)

    fun getTotalSucosByDateRange(
        startDate: Date,
        endDate: Date,
    ): Flow<Int?> = vendaDao.getTotalSucosByDateRange(startDate, endDate)
}
