package com.example.caderneta.repository

import com.example.caderneta.data.dao.ProdutoDao
import com.example.caderneta.data.entity.Produto
import kotlinx.coroutines.flow.Flow

class ProdutoRepository(private val produtoDao: ProdutoDao) {

    fun getAllProdutos(): Flow<List<Produto>> = produtoDao.getAllProdutos()

    suspend fun getProdutoById(id: Long): Produto? = produtoDao.getProdutoById(id)

    suspend fun insertProduto(produto: Produto): Long = produtoDao.insertProduto(produto)

    suspend fun updateProduto(produto: Produto) = produtoDao.updateProduto(produto)

    suspend fun deleteProduto(produto: Produto) = produtoDao.deleteProduto(produto)
}