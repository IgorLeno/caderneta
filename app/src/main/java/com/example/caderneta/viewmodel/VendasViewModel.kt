package com.example.caderneta.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.*
import com.example.caderneta.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

class VendasViewModel(
    private val clienteRepository: ClienteRepository,
    private val localRepository: LocalRepository,
    private val produtoRepository: ProdutoRepository,
    private val vendaRepository: VendaRepository,
    private val itemVendaRepository: ItemVendaRepository,
    private val configuracoesRepository: ConfiguracoesRepository,
    private val operacaoRepository: OperacaoRepository,
    private val contaRepository: ContaRepository
) : ViewModel() {

    private val _clientes = MutableStateFlow<List<Cliente>>(emptyList())
    val clientes: StateFlow<List<Cliente>> = _clientes

    private val _locais = MutableStateFlow<List<Local>>(emptyList())
    val locais: StateFlow<List<Local>> = _locais

    private val _localSelecionado = MutableStateFlow<Local?>(null)
    val localSelecionado: StateFlow<Local?> = _localSelecionado

    private val _produtos = MutableStateFlow<List<Produto>>(emptyList())
    val produtos: StateFlow<List<Produto>> = _produtos

    private val _configuracoes = MutableStateFlow<Configuracoes?>(null)
    val configuracoes: StateFlow<Configuracoes?> = _configuracoes

    private val _clienteStates = MutableStateFlow<Map<Long, ClienteState>>(emptyMap())
    val clienteStates: StateFlow<Map<Long, ClienteState>> = _clienteStates.asStateFlow()

    private val _valorTotal = MutableStateFlow<Pair<Long, Double>>(0L to 0.0)
    val valorTotal: StateFlow<Pair<Long, Double>> = _valorTotal.asStateFlow()

    private val _previaPagamento = MutableStateFlow(0.0)
    val previaPagamento: StateFlow<Double> = _previaPagamento

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _saldoAtualizado = MutableSharedFlow<Long>()
    val saldoAtualizado = _saldoAtualizado.asSharedFlow()


    init {
        carregarDados()
    }

    data class ClienteState(
        val clienteId: Long,
        var modoOperacao: ModoOperacao? = null,
        var tipoTransacao: TipoTransacao? = null,
        var quantidadeSalgados: Int = 0,
        var quantidadeSucos: Int = 0,
        var valorTotal: Double = 0.0
    )

    private fun carregarDados() {
        viewModelScope.launch {
            try {
                launch {
                    configuracoesRepository.getConfiguracoes().collect { configuracoes ->
                        _configuracoes.value = configuracoes
                        Log.d("VendasViewModel", "Configurações carregadas: $configuracoes")
                    }
                }

                launch {
                    localRepository.getAllLocais().collect { locais ->
                        _locais.value = locais
                        Log.d("VendasViewModel", "Locais carregados: ${locais.size}")
                    }
                }

                launch {
                    produtoRepository.getAllProdutos().collect { produtos ->
                        _produtos.value = produtos
                        Log.d("VendasViewModel", "Produtos carregados: ${produtos.size}")
                    }
                }
            } catch (e: Exception) {
                Log.e("VendasViewModel", "Erro ao carregar dados", e)
                _error.value = "Erro ao carregar dados: ${e.message}"
            }
        }
    }

    fun selecionarLocal(localId: Long) {
        viewModelScope.launch {
            try {
                _localSelecionado.value = localRepository.getLocalById(localId)
                _clientes.value = clienteRepository.getClientesByLocal(localId)
            } catch (e: Exception) {
                _error.value = "Erro ao selecionar local: ${e.message}"
            }
        }
    }

    fun getClienteState(clienteId: Long): ClienteState? {
        return _clienteStates.value[clienteId]
    }

    fun selecionarModoOperacao(cliente: Cliente, modoOperacao: ModoOperacao?) {
        val currentState = _clienteStates.value[cliente.id]
        val newState = if (currentState == null || currentState.modoOperacao != modoOperacao) {
            ClienteState(clienteId = cliente.id, modoOperacao = modoOperacao)
        } else {
            currentState.copy(
                modoOperacao = null,
                tipoTransacao = null,
                quantidadeSalgados = 0,
                quantidadeSucos = 0,
                valorTotal = 0.0
            )
        }
        _clienteStates.value = _clienteStates.value.toMutableMap().apply { put(cliente.id, newState) }
        calcularValorTotal(newState)
    }

    fun selecionarTipoTransacao(cliente: Cliente, tipoTransacao: TipoTransacao?) {
        val clienteState = _clienteStates.value[cliente.id] ?: return
        val newTipoTransacao = if (clienteState.tipoTransacao == tipoTransacao) null else tipoTransacao
        val newState = clienteState.copy(
            tipoTransacao = newTipoTransacao,
            quantidadeSalgados = 0,
            quantidadeSucos = 0,
            valorTotal = 0.0
        )
        _clienteStates.value = _clienteStates.value.toMutableMap().apply { put(cliente.id, newState) }
        calcularValorTotal(newState)
    }

    fun updateQuantidadeSalgados(clienteId: Long, quantidade: Int) {
        val clienteState = _clienteStates.value[clienteId] ?: return
        val newState = clienteState.copy(quantidadeSalgados = quantidade)
        _clienteStates.value = _clienteStates.value.toMutableMap().apply { put(clienteId, newState) }
        calcularValorTotal(newState)
    }

    fun updateQuantidadeSucos(clienteId: Long, quantidade: Int) {
        val clienteState = _clienteStates.value[clienteId] ?: return
        val newState = clienteState.copy(quantidadeSucos = quantidade)
        _clienteStates.value = _clienteStates.value.toMutableMap().apply { put(clienteId, newState) }
        calcularValorTotal(newState)
    }

    private fun calcularValorTotal(state: ClienteState) {
        val config = _configuracoes.value ?: return
        state.valorTotal = when (state.modoOperacao) {
            ModoOperacao.VENDA -> when (state.tipoTransacao) {
                TipoTransacao.A_VISTA -> (state.quantidadeSalgados * config.precoSalgadoVista) + (state.quantidadeSucos * config.precoSucoVista)
                TipoTransacao.A_PRAZO -> (state.quantidadeSalgados * config.precoSalgadoPrazo) + (state.quantidadeSucos * config.precoSucoPrazo)
                null -> 0.0
            }
            ModoOperacao.PROMOCAO -> when (state.tipoTransacao) {
                TipoTransacao.A_VISTA -> config.promo1Vista
                TipoTransacao.A_PRAZO -> config.promo2Prazo
                null -> 0.0
            }
            else -> 0.0
        }

        viewModelScope.launch {
            _valorTotal.emit(state.clienteId to state.valorTotal)
            _clienteStates.update { currentStates ->
                currentStates.toMutableMap().apply {
                    put(state.clienteId, state)
                }
            }
        }
    }

    fun confirmarOperacao(clienteId: Long) {
        val clienteState = _clienteStates.value[clienteId] ?: return
        when (clienteState.modoOperacao) {
            ModoOperacao.VENDA, ModoOperacao.PROMOCAO -> confirmarVenda(clienteId)
            ModoOperacao.PAGAMENTO -> confirmarPagamento(clienteId)
            else -> {}
        }
    }

    private fun confirmarVenda(clienteId: Long) {
        viewModelScope.launch {
            try {
                val clienteState = _clienteStates.value[clienteId] ?: throw IllegalStateException("Estado do cliente não encontrado")
                val localId = _localSelecionado.value?.id ?: throw IllegalStateException("Local não selecionado")

                val operacao = Operacao(
                    clienteId = clienteId,
                    tipoOperacao = if (clienteState.modoOperacao == ModoOperacao.PROMOCAO) "Promo" else "Venda",
                    valor = clienteState.valorTotal,
                    data = Date()
                )
                val operacaoId = operacaoRepository.insertOperacao(operacao)

                val venda = Venda(
                    operacaoId = operacaoId,
                    clienteId = clienteId,
                    localId = localId,
                    data = Date(),
                    transacao = if (clienteState.tipoTransacao == TipoTransacao.A_VISTA) "a_vista" else "a_prazo",
                    quantidadeSalgados = clienteState.quantidadeSalgados,
                    quantidadeSucos = clienteState.quantidadeSucos,
                    valor = clienteState.valorTotal
                )
                vendaRepository.insertVenda(venda)

                if (clienteState.tipoTransacao == TipoTransacao.A_PRAZO) {
                    atualizarSaldoCliente(clienteId, clienteState.valorTotal)
                }

                resetClienteState(clienteId)
                _operacaoConfirmada.value = OperacaoConfirmada.Venda
            } catch (e: Exception) {
                _error.value = "Erro ao confirmar venda: ${e.message}"
            }
        }
    }

    private fun confirmarPagamento(clienteId: Long) {
        viewModelScope.launch {
            try {
                val clienteState = _clienteStates.value[clienteId] ?: throw IllegalStateException("Estado do cliente não encontrado")
                val valorPago = clienteState.valorTotal

                val operacao = Operacao(
                    clienteId = clienteId,
                    tipoOperacao = "Pagamento",
                    valor = valorPago,
                    data = Date()
                )
                operacaoRepository.insertOperacao(operacao)

                atualizarSaldoCliente(clienteId, -valorPago)

                resetClienteState(clienteId)
                _operacaoConfirmada.value = OperacaoConfirmada.Pagamento
            } catch (e: Exception) {
                _error.value = "Erro ao confirmar pagamento: ${e.message}"
            }
        }
    }

    private fun atualizarSaldoCliente(clienteId: Long, valor: Double) {
        viewModelScope.launch {
            val contaExistente = contaRepository.getContaByCliente(clienteId)
            if (contaExistente == null) {
                val novaConta = Conta(clienteId = clienteId, saldo = valor)
                contaRepository.insertConta(novaConta)
            } else {
                contaRepository.atualizarSaldo(clienteId, valor)
            }
            _saldoAtualizado.emit(clienteId)
        }
    }

    fun cancelarOperacao(clienteId: Long) {
        resetClienteState(clienteId)
    }

    private fun resetClienteState(clienteId: Long) {
        _clienteStates.update { currentStates ->
            currentStates.toMutableMap().apply {
                put(clienteId, ClienteState(clienteId = clienteId))
            }
        }
        viewModelScope.launch {
            _valorTotal.emit(clienteId to 0.0)
            _clienteStateUpdates.emit(clienteId) // Emit the client ID
        }
    }


    fun resetOperacaoConfirmada() {
        _operacaoConfirmada.value = null
    }


    private val _clienteStateUpdates = MutableSharedFlow<Long>()
    val clienteStateUpdates = _clienteStateUpdates.asSharedFlow()


    private val _operacaoConfirmada = MutableStateFlow<OperacaoConfirmada?>(null)
    val operacaoConfirmada: StateFlow<OperacaoConfirmada?> = _operacaoConfirmada.asStateFlow()

    sealed class OperacaoConfirmada {
        object Venda : OperacaoConfirmada()
        object Pagamento : OperacaoConfirmada()
    }


    fun calcularPreviaPagamento(clienteId: Long, valorPagamento: Double) {
        viewModelScope.launch {
            try {
                val conta = contaRepository.getContaByCliente(clienteId)
                val saldoDevedor = conta?.saldo ?: 0.0
                val valorRestante = (saldoDevedor - valorPagamento).coerceAtLeast(0.0)
                _previaPagamento.value = valorRestante
            } catch (e: Exception) {
                _error.value = "Erro ao calcular prévia de pagamento: ${e.message}"
            }
        }
    }

    fun updateValorTotal(clienteId: Long, valor: Double) {
        viewModelScope.launch {
            _valorTotal.emit(clienteId to valor)
        }
    }

    fun addLocal(nome: String, parentId: Long? = null) {
        viewModelScope.launch {
            try {
                val parentLevel = parentId?.let { localRepository.getLocalById(it)?.level ?: -1 } ?: -1
                val newLocal = Local(
                    nome = nome,
                    endereco = "",
                    parentId = parentId,
                    level = parentLevel + 1
                )
                val newId = localRepository.insertLocal(newLocal)
                Log.d("VendasViewModel", "Novo local adicionado com ID: $newId")
                reloadLocais()
            } catch (e: Exception) {
                Log.e("VendasViewModel", "Erro ao adicionar local", e)
                _error.value = "Erro ao adicionar local: ${e.message}"
            }
        }
    }

    fun editLocal(local: Local) {
        viewModelScope.launch {
            try {
                localRepository.updateLocal(local)
                carregarDados()
            } catch (e: Exception) {
                _error.value = "Erro ao editar local: ${e.message}"
            }
        }
    }

    fun deleteLocal(local: Local) {
        viewModelScope.launch {
            try {
                localRepository.deleteLocal(local)
                reloadLocais()
            } catch (e: Exception) {
                _error.value = "Erro ao deletar local: ${e.message}"
            }
        }
    }

    fun reloadLocais() {
        viewModelScope.launch {
            try {
                _locais.value = localRepository.getAllLocais().first()
            } catch (e: Exception) {
                _error.value = "Erro ao recarregar locais: ${e.message}"
            }
        }
    }

    fun searchLocais(query: String) {
        viewModelScope.launch {
            try {
                _locais.value = localRepository.buscarLocais(query)
            } catch (e: Exception) {
                _error.value = "Erro ao buscar locais: ${e.message}"
            }
        }
    }

    fun toggleLocalExpansion(local: Local) {
        viewModelScope.launch {
            try {
                val updatedLocal = local.copy(isExpanded = !local.isExpanded)
                localRepository.updateLocal(updatedLocal)
                val updatedLocais = localRepository.getAllLocais().first()
                _locais.value = updatedLocais
                Log.d("VendasViewModel", "Toggle expansion for local ${local.id}. New state: ${updatedLocal.isExpanded}")
                Log.d("VendasViewModel", "Updated locais: ${updatedLocais.map { "${it.id}:${it.nome}(${it.parentId}):${it.isExpanded}" }}")
            } catch (e: Exception) {
                _error.value = "Erro ao alternar expansão do local: ${e.message}"
            }
        }
    }

    fun getSublocais(parentId: Long?): List<Local> {
        return _locais.value.filter { it.parentId == parentId }
    }

    fun addCliente(nome: String, telefone: String, localId: Long, sublocal1Id: Long?, sublocal2Id: Long?, sublocal3Id: Long?) {
        viewModelScope.launch {
            try {
                val novoCliente = Cliente(
                    nome = nome,
                    telefone = telefone,
                    localId = localId,
                    sublocal1Id = sublocal1Id,
                    sublocal2Id = sublocal2Id,
                    sublocal3Id = sublocal3Id
                )
                clienteRepository.insertCliente(novoCliente)
                _clientes.value = clienteRepository.getAllClientes().first()
            } catch (e: Exception) {
                _error.value = "Erro ao adicionar cliente: ${e.message}"
            }
        }
    }

    fun getLocalHierarchy(localId: Long): List<Local> {
        val hierarchy = mutableListOf<Local>()
        var currentId: Long? = localId

        while (currentId != null) {
            val currentLocal = _locais.value.find { it.id == currentId }
            if (currentLocal != null) {
                hierarchy.add(0, currentLocal)
                currentId = currentLocal.parentId
            } else {
                break
            }
        }

        return hierarchy
    }

    fun clearError() {
        _error.value = null
    }
}

class VendasViewModelFactory(
    private val clienteRepository: ClienteRepository,
    private val localRepository: LocalRepository,
    private val produtoRepository: ProdutoRepository,
    private val vendaRepository: VendaRepository,
    private val itemVendaRepository: ItemVendaRepository,
    private val configuracoesRepository: ConfiguracoesRepository,
    private val operacaoRepository: OperacaoRepository,
    private val contaRepository: ContaRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VendasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VendasViewModel(
                clienteRepository,
                localRepository,
                produtoRepository,
                vendaRepository,
                itemVendaRepository,
                configuracoesRepository,
                operacaoRepository,
                contaRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
