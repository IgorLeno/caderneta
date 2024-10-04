package com.example.caderneta.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Produto
import com.example.caderneta.data.entity.TipoProduto
import com.example.caderneta.repository.ProdutoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConfiguracoesViewModel(
    private val produtoRepository: ProdutoRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _produtos = MutableStateFlow<List<Produto>>(emptyList())
    val produtos: StateFlow<List<Produto>> = _produtos

    private val _precoAtualizado = MutableStateFlow<Produto?>(null)
    val precoAtualizado: StateFlow<Produto?> = _precoAtualizado

    init {
        carregarProdutos()
    }

    private fun carregarProdutos() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                produtoRepository.getAllProdutos().collect {
                    _produtos.value = it
                }
            } catch (e: Exception) {
                _error.value = "Erro ao carregar produtos: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun atualizarPrecoProduto(produtoId: Long, novoPreco: Double) {
        viewModelScope.launch {
            try {
                val produto = produtoRepository.getProdutoById(produtoId)
                produto?.let {
                    val produtoAtualizado = it.copy(preco = novoPreco)
                    produtoRepository.updateProduto(produtoAtualizado)
                    _precoAtualizado.value = produtoAtualizado
                }
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar preço: ${e.message}"
            }
        }
    }

    fun adicionarNovoProduto(nome: String, preco: Double, tipo: TipoProduto) {
        viewModelScope.launch {
            try {
                val novoProduto = Produto(nome = nome, preco = preco, tipo = tipo)
                produtoRepository.insertProduto(novoProduto)
                carregarProdutos() // Recarrega a lista de produtos
            } catch (e: Exception) {
                _error.value = "Erro ao adicionar produto: ${e.message}"
            }
        }
    }

    fun removerProduto(produtoId: Long) {
        viewModelScope.launch {
            try {
                val produto = produtoRepository.getProdutoById(produtoId)
                produto?.let {
                    produtoRepository.deleteProduto(it)
                    carregarProdutos() // Recarrega a lista de produtos
                }
            } catch (e: Exception) {
                _error.value = "Erro ao remover produto: ${e.message}"
            }
        }
    }
}

class ConfiguracoesViewModelFactory(
    private val produtoRepository: ProdutoRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConfiguracoesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConfiguracoesViewModel(produtoRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}