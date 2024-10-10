package com.example.caderneta.viewmodel

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ConsultasViewModel(
    private val clienteRepository: ClienteRepository,
    private val vendaRepository: VendaRepository,
    private val localRepository: LocalRepository,
    private val contaRepository: ContaRepository
) : ViewModel() {

    private val _locais = MutableStateFlow<List<Local>>(emptyList())
    val locais: StateFlow<List<Local>> = _locais

    private val _clientes = MutableStateFlow<List<Cliente>>(emptyList())
    val clientes: StateFlow<List<Cliente>> = _clientes

    private val _vendasPorCliente = MutableStateFlow<Map<Long, List<Venda>>>(emptyMap())
    val vendasPorCliente: StateFlow<Map<Long, List<Venda>>> = _vendasPorCliente

    private val _clientesComSaldo = MutableStateFlow<List<Pair<Cliente, Double>>>(emptyList())
    val clientesComSaldo: StateFlow<List<Pair<Cliente, Double>>> = _clientesComSaldo

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
                    _clientesComSaldo.value = emptyList()
                }
                ModoBusca.POR_NOME -> {
                    buscarClientesComSaldo(query)
                    _locais.value = emptyList()
                }
            }
        }
    }

    fun buscarClientesComSaldo(query: String) {
        viewModelScope.launch {
            val clientes = clienteRepository.buscarClientes(query)
            val clientesComSaldo = clientes.map { cliente ->
                val saldo = getSaldoCliente(cliente.id)
                Pair(cliente, saldo)
            }
            _clientesComSaldo.value = clientesComSaldo
        }
    }

    suspend fun getSaldoCliente(clienteId: Long): Double {
        return contaRepository.getContaByCliente(clienteId).first()?.saldo ?: 0.0
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