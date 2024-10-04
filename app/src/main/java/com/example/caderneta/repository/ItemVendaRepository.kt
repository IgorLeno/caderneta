package com.example.caderneta.repository

import com.example.caderneta.data.dao.ItemVendaDao
import com.example.caderneta.data.entity.ItemVenda
import kotlinx.coroutines.flow.Flow

class ItemVendaRepository(private val itemVendaDao: ItemVendaDao) {

    fun getItensVendaByVendaId(vendaId: Long): Flow<List<ItemVenda>> =
        itemVendaDao.getItensVendaByVendaId(vendaId)

    suspend fun insertItemVenda(itemVenda: ItemVenda): Long = itemVendaDao.insertItemVenda(itemVenda)

    suspend fun updateItemVenda(itemVenda: ItemVenda) = itemVendaDao.updateItemVenda(itemVenda)

    suspend fun deleteItemVenda(itemVenda: ItemVenda) = itemVendaDao.deleteItemVenda(itemVenda)
}