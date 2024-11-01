package com.example.caderneta.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ConsultasViewModel(
    private val clienteRepository: ClienteRepository,
    private val vendaRepository: VendaRepository,
    private val localRepository: LocalRepository,
    private val contaRepository: ContaRepository
) : ViewModel() {

    private val _locais = MutableStateFlow<List<Local>>(emptyList())
    val locais: StateFlow<List<Local>> = _locais


    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error


    private val _localSelecionado = MutableStateFlow<Local?>(null)
    val localSelecionado: StateFlow<Local?> = _localSelecionado

    private val _vendasPorCliente = MutableStateFlow<Map<Long, List<Venda>>>(emptyMap())
    val vendasPorCliente: StateFlow<Map<Long, List<Venda>>> = _vendasPorCliente

    private val _clientesComSaldo = MutableStateFlow<List<Pair<Cliente, Double>>>(emptyList())
    val clientesComSaldo: StateFlow<List<Pair<Cliente, Double>>> = _clientesComSaldo

    private val _searchQuery = MutableStateFlow("")

    init {
        carregarLocais()
    }

    private fun carregarLocais() {
        viewModelScope.launch {
            try {
                localRepository.getAllLocais().collect { locaisList ->
                    _locais.value = locaisList
                    Log.d("ConsultasViewModel", "Locais carregados: ${locaisList.size}")
                }
            } catch (e: Exception) {
                Log.e("ConsultasViewModel", "Erro ao carregar locais", e)
            }
        }
    }

    private fun handleError(e: Exception) {
        _error.value = e.message
        Log.e("ConsultasViewModel", "Erro:", e)
    }


    fun selecionarLocal(localId: Long) {
        viewModelScope.launch {
            try {
                val local = localRepository.getLocalById(localId)
                if (local != null) {
                    // Emitir o local selecionado
                    _localSelecionado.value = local

                    // Buscar clientes considerando a hierarquia
                    clienteRepository.getClientesByLocalHierarchy(localId)
                        .collect { clientes ->
                            // Atualizar a lista de clientes
                            _clientesComSaldo.value = clientes.map { cliente ->
                                val saldo = contaRepository.getContaByCliente(cliente.id)?.saldo ?: 0.0
                                cliente to saldo
                            }
                        }
                }
            } catch (e: Exception) {
                _error.value = "Erro ao selecionar local: ${e.message}"
            }
        }
    }


    fun buscarClientes(query: String) {
        viewModelScope.launch {
            _searchQuery.value = query
            try {
                when (val localAtual = _localSelecionado.value) {
                    null -> {
                        // Busca global em todos os clientes
                        val clientes = if (query.isEmpty()) {
                            clienteRepository.getAllClientes().first()
                        } else {
                            clienteRepository.buscarClientes(query)
                        }
                        atualizarClientesComSaldo(clientes, query)
                    }
                    else -> {
                        // Busca dentro do local selecionado
                        clienteRepository.getClientesByLocalHierarchy(localAtual.id).collect { clientes ->
                            val clientesFiltrados = if (query.isEmpty()) {
                                clientes
                            } else {
                                clientes.filter {
                                    it.nome.contains(query, ignoreCase = true)
                                }
                            }
                            atualizarClientesComSaldo(clientesFiltrados, query)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ConsultasViewModel", "Erro ao buscar clientes", e)
            }
        }
    }

    fun buscarLocais(query: String) {
        viewModelScope.launch {
            try {
                _locais.value = if (query.isEmpty()) {
                    localRepository.getAllLocais().first()
                } else {
                    localRepository.buscarLocais(query)
                }
            } catch (e: Exception) {
                Log.e("ConsultasViewModel", "Erro ao buscar locais", e)
            }
        }
    }

    private suspend fun atualizarClientesComSaldo(clientes: List<Cliente>, query: String) {
        val clientesFiltrados = if (query.isNotEmpty()) {
            clientes.filter { it.nome.contains(query, ignoreCase = true) }
        } else {
            clientes
        }

        val clientesComSaldoAtualizado = clientesFiltrados.map { cliente ->
            val saldo = getSaldoCliente(cliente.id)
            Pair(cliente, saldo)
        }
        _clientesComSaldo.value = clientesComSaldoAtualizado
    }

    suspend fun getSaldoCliente(clienteId: Long): Double {
        return contaRepository.getContaByCliente(clienteId)?.saldo ?: 0.0
    }

    fun toggleLocalExpansion(local: Local) {
        viewModelScope.launch {
            try {
                val updatedLocal = local.copy(isExpanded = !local.isExpanded)
                localRepository.updateLocal(updatedLocal)
                // Recarrega a lista de locais para refletir a mudança
                _locais.value = localRepository.getAllLocais().first()
            } catch (e: Exception) {
                Log.e("ConsultasViewModel", "Erro ao alternar expansão do local", e)
            }
        }
    }

    fun clearSearch() {
        viewModelScope.launch {
            _searchQuery.value = ""
        }
    }

    fun carregarVendasPorCliente(clienteId: Long) {
        viewModelScope.launch {
            vendaRepository.getVendasByCliente(clienteId).collect { vendas ->
                _vendasPorCliente.value = _vendasPorCliente.value.toMutableMap().apply {
                    put(clienteId, vendas)
                }
            }
        }
    }

    fun limparExtrato(clienteId: Long) {
        _vendasPorCliente.value = _vendasPorCliente.value.toMutableMap().apply {
            remove(clienteId)
        }
    }
}

class ConsultasViewModelFactory(
    private val clienteRepository: ClienteRepository,
    private val vendaRepository: VendaRepository,
    private val localRepository: LocalRepository,
    private val contaRepository: ContaRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConsultasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConsultasViewModel(clienteRepository, vendaRepository, localRepository, contaRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}