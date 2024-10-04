package com.example.caderneta.data.dao

import androidx.room.*
import com.example.caderneta.data.entity.Produto
import kotlinx.coroutines.flow.Flow

@Dao
interface ProdutoDao {
    @Query("SELECT * FROM produtos")
    fun getAllProdutos(): Flow<List<Produto>>

    @Query("SELECT * FROM produtos WHERE id = :id")
    suspend fun getProdutoById(id: Long): Produto?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduto(produto: Produto): Long

    @Update
    suspend fun updateProduto(produto: Produto)

    @Delete
    suspend fun deleteProduto(produto: Produto)
}