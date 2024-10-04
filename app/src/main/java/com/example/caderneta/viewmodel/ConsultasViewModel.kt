package com.example.caderneta.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConsultasViewModel(
    private val clienteRepository: ClienteRepository,
    private val vendaRepository: VendaRepository,
    private val localRepository: LocalRepository
) : ViewModel() {

    private val _locais = MutableStateFlow<List<Local>>(emptyList())
    val locais: StateFlow<List<Local>> = _locais

    private val _clientes = MutableStateFlow<List<Cliente>>(emptyList())
    val clientes: StateFlow<List<Cliente>> = _clientes

    private val _vendasPorCliente = MutableStateFlow<Map<Long, List<Venda>>>(emptyMap())
    val vendasPorCliente: StateFlow<Map<Long, List<Venda>>> = _vendasPorCliente

    private val _modoBusca = MutableStateFlow(ModoBusca.POR_PREDIO)
    val modoBusca: StateFlow<ModoBusca> = _modoBusca

    fun setModoBusca(modo: ModoBusca) {
        _modoBusca.value = modo
    }

    fun buscar(query: String) {
        viewModelScope.launch {
            when (_modoBusca.value) {
                ModoBusca.POR_PREDIO -> {
                    _locais.value = localRepository.buscarLocais(query)
                    _clientes.value = emptyList()
                }
                ModoBusca.POR_NOME -> {
                    _clientes.value = clienteRepository.buscarClientes(query)
                    _locais.value = emptyList()
                }
            }
        }
    }

    fun carregarClientesPorLocal(localId: Long) {
        viewModelScope.launch {
            _clientes.value = clienteRepository.getClientesByLocal(localId)
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

    enum class ModoBusca {
        POR_PREDIO, POR_NOME
    }
}

class ConsultasViewModelFactory(
    private val clienteRepository: ClienteRepository,
    private val vendaRepository: VendaRepository,
    private val localRepository: LocalRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConsultasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConsultasViewModel(clienteRepository, vendaRepository, localRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}