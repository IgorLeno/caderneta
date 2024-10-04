package com.example.caderneta.data.dao

import androidx.room.*
import com.example.caderneta.data.entity.ItemVenda
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemVendaDao {
    @Query("SELECT * FROM itens_venda WHERE vendaId = :vendaId")
    fun getItensVendaByVendaId(vendaId: Long): Flow<List<ItemVenda>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItemVenda(itemVenda: ItemVenda): Long

    @Update
    suspend fun updateItemVenda(itemVenda: ItemVenda)

    @Delete
    suspend fun deleteItemVenda(itemVenda: ItemVenda)
}