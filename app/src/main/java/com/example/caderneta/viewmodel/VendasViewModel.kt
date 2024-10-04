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
    private val configuracaoRepository: ConfiguracaoRepository
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

    private val _clienteSelecionado = MutableStateFlow<Cliente?>(null)
    val clienteSelecionado: StateFlow<Cliente?> = _clienteSelecionado

    private val _modoOperacaoAtual = MutableStateFlow<ModoOperacao?>(null)
    val modoOperacaoAtual: StateFlow<ModoOperacao?> = _modoOperacaoAtual

    private val _tipoTransacaoSelecionado = MutableStateFlow<TipoTransacao?>(null)
    val tipoTransacaoSelecionado: StateFlow<TipoTransacao?> = _tipoTransacaoSelecionado

    private val _quantidadeSalgados = MutableStateFlow(0)
    val quantidadeSalgados: StateFlow<Int> = _quantidadeSalgados

    private val _quantidadeSucos = MutableStateFlow(0)
    val quantidadeSucos: StateFlow<Int> = _quantidadeSucos

    private val _valorTotal = MutableStateFlow(0.0)
    val valorTotal: StateFlow<Double> = _valorTotal

    private val _previaPagamento = MutableStateFlow(0.0)
    val previaPagamento: StateFlow<Double> = _previaPagamento

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _contadoresVisibles = MutableStateFlow(false)
    val contadoresVisibles: StateFlow<Boolean> = _contadoresVisibles

    private val _clienteStates = MutableStateFlow<Map<Long, ClienteState>>(emptyMap())
    val clienteStates: StateFlow<Map<Long, ClienteState>> = _clienteStates.asStateFlow()

    init {
        carregarDados()
    }

    data class ClienteState(
        var modoOperacao: ModoOperacao? = null,
        var tipoTransacao: TipoTransacao? = null,
        var quantidadeSalgados: Int = 0,
        var quantidadeSucos: Int = 0,
        var valorTotal: Double = 0.0
    )


    private fun carregarDados() {
        viewModelScope.launch {
            try {
                localRepository.getAllLocais().collect { locais ->
                    _locais.value = locais
                    Log.d("VendasViewModel", "Locais carregados: ${locais.size}, IDs: ${locais.map { it.id }}")
                }
                configuracaoRepository.getConfiguracoes().collect { configuracoes ->
                    _configuracoes.value = configuracoes
                }
                produtoRepository.getAllProdutos().collect { produtos ->
                    _produtos.value = produtos
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

    fun selecionarModoOperacao(cliente: Cliente, modoOperacao: ModoOperacao, tipoTransacao: TipoTransacao? = null) {
        val clienteState = _clienteStates.value.getOrDefault(cliente.id, ClienteState())
        val novoState = clienteState.copy(
            modoOperacao = modoOperacao,
            tipoTransacao = tipoTransacao,
            quantidadeSalgados = 0,
            quantidadeSucos = 0,
            valorTotal = 0.0
        )

        when (modoOperacao) {
            ModoOperacao.VENDA, ModoOperacao.PROMOCAO -> {
                if (modoOperacao == ModoOperacao.PROMOCAO) {
                    aplicarPromocao(novoState, tipoTransacao == TipoTransacao.A_VISTA)
                }
            }
            ModoOperacao.PAGAMENTO -> {
                _previaPagamento.value = 0.0
            }
        }
        calcularValorTotal(novoState)
        _clienteStates.value = _clienteStates.value.toMutableMap().apply { put(cliente.id, novoState) }
    }

    private fun aplicarPromocao(state: ClienteState, aVista: Boolean) {
        val config = _configuracoes.value ?: return
        if (aVista) {
            state.quantidadeSalgados = config.quantidadeSalgadosPromocao1
            state.quantidadeSucos = config.quantidadeSucosPromocao1
            state.valorTotal = config.valorPromocao1
        } else {
            state.quantidadeSalgados = config.quantidadeSalgadosPromocao2
            state.quantidadeSucos = config.quantidadeSucosPromocao2
            state.valorTotal = config.valorPromocao2
        }
    }

    fun updateContadoresVisibility(clienteId: Long, visible: Boolean) {
        val clienteState = _clienteStates.value[clienteId] ?: return
        _clienteStates.value = _clienteStates.value.toMutableMap().apply {
            put(clienteId, clienteState.copy(quantidadeSalgados = if (visible) clienteState.quantidadeSalgados else 0,
                quantidadeSucos = if (visible) clienteState.quantidadeSucos else 0))
        }
    }

    fun updateQuantidadeSalgados(clienteId: Long, quantidade: Int) {
        val clienteState = _clienteStates.value[clienteId] ?: return
        if (clienteState.modoOperacao != ModoOperacao.PROMOCAO) {
            val novoState = clienteState.copy(quantidadeSalgados = quantidade)
            calcularValorTotal(novoState)
            _clienteStates.value = _clienteStates.value.toMutableMap().apply { put(clienteId, novoState) }
        }
    }

    fun updateQuantidadeSucos(clienteId: Long, quantidade: Int) {
        val clienteState = _clienteStates.value[clienteId] ?: return
        if (clienteState.modoOperacao != ModoOperacao.PROMOCAO) {
            val novoState = clienteState.copy(quantidadeSucos = quantidade)
            calcularValorTotal(novoState)
            _clienteStates.value = _clienteStates.value.toMutableMap().apply { put(clienteId, novoState) }
        }
    }

    private fun calcularValorTotal(state: ClienteState) {
        val config = configuracoes.value ?: return
        state.valorTotal = when (state.modoOperacao) {
            ModoOperacao.VENDA -> when (state.tipoTransacao) {
                TipoTransacao.A_VISTA -> (state.quantidadeSalgados * config.precoSalgadoVista) + (state.quantidadeSucos * config.precoSucoVista)
                TipoTransacao.A_PRAZO -> (state.quantidadeSalgados * config.precoSalgadoPrazo) + (state.quantidadeSucos * config.precoSucoPrazo)
                null -> 0.0
            }
            ModoOperacao.PROMOCAO -> if (state.tipoTransacao == TipoTransacao.A_VISTA) config.valorPromocao1 else config.valorPromocao2
            ModoOperacao.PAGAMENTO, null -> 0.0
        }
    }

    fun confirmarVenda(clienteId: Long) {
        viewModelScope.launch {
            try {
                val clienteState = _clienteStates.value[clienteId] ?: throw IllegalStateException("Estado do cliente não encontrado")
                val cliente = _clientes.value.find { it.id == clienteId } ?: throw IllegalStateException("Cliente não encontrado")
                val localId = _localSelecionado.value?.id ?: throw IllegalStateException("Local não selecionado")

                val venda = Venda(
                    clienteId = clienteId,
                    localId = localId,
                    data = Date(),
                    total = clienteState.valorTotal,
                    pago = clienteState.tipoTransacao == TipoTransacao.A_VISTA,
                    quantidadeSalgados = clienteState.quantidadeSalgados,
                    quantidadeSucos = clienteState.quantidadeSucos
                )

                val vendaId = vendaRepository.insertVenda(venda)
                inserirItensVenda(vendaId, clienteState.quantidadeSalgados, clienteState.quantidadeSucos, clienteState.tipoTransacao ?: throw IllegalStateException("Tipo de transação não selecionado"))

                val clienteAtualizado = cliente.copy(
                    valorDevido = if (clienteState.tipoTransacao == TipoTransacao.A_VISTA) cliente.valorDevido else cliente.valorDevido + clienteState.valorTotal,
                    quantidadeSalgados = cliente.quantidadeSalgados + clienteState.quantidadeSalgados,
                    quantidadeSucos = cliente.quantidadeSucos + clienteState.quantidadeSucos
                )
                clienteRepository.updateCliente(clienteAtualizado)

                cancelarOperacao(clienteId)
                carregarDados()
            } catch (e: Exception) {
                _error.value = "Erro ao confirmar venda: ${e.message}"
            }
        }
    }

    fun cancelarOperacao(clienteId: Long) {
        _clienteStates.value = _clienteStates.value.toMutableMap().apply { remove(clienteId) }
    }

    private suspend fun inserirItensVenda(vendaId: Long, quantidadeSalgados: Int, quantidadeSucos: Int, tipoTransacao: TipoTransacao) {
        val config = _configuracoes.value ?: return
        val salgado = produtos.value.find { it.tipo == TipoProduto.SALGADO }
        val suco = produtos.value.find { it.tipo == TipoProduto.SUCO }

        val precoSalgado = when (_modoOperacaoAtual.value) {
            ModoOperacao.PROMOCAO -> 0.0
            ModoOperacao.VENDA -> if (tipoTransacao == TipoTransacao.A_VISTA) config.precoSalgadoVista else config.precoSalgadoPrazo
            else -> 0.0
        }

        val precoSuco = when (_modoOperacaoAtual.value) {
            ModoOperacao.PROMOCAO -> 0.0
            ModoOperacao.VENDA -> if (tipoTransacao == TipoTransacao.A_VISTA) config.precoSucoVista else config.precoSucoPrazo
            else -> 0.0
        }

        if (salgado != null && quantidadeSalgados > 0) {
            itemVendaRepository.insertItemVenda(
                ItemVenda(
                    vendaId = vendaId,
                    produtoId = salgado.id,
                    quantidade = quantidadeSalgados,
                    precoUnitario = precoSalgado
                )
            )
        }

        if (suco != null && quantidadeSucos > 0) {
            itemVendaRepository.insertItemVenda(
                ItemVenda(
                    vendaId = vendaId,
                    produtoId = suco.id,
                    quantidade = quantidadeSucos,
                    precoUnitario = precoSuco
                )
            )
        }
    }

    fun iniciarModoPagamento(cliente: Cliente) {
        selecionarModoOperacao(cliente, ModoOperacao.PAGAMENTO)
    }

    fun calcularPreviaPagamento(clienteId: Long, valorPagamento: Double) {
        val clienteState = _clienteStates.value[clienteId] ?: return
        val cliente = _clientes.value.find { it.id == clienteId } ?: return
        clienteState.valorTotal = (cliente.valorDevido - valorPagamento).coerceAtLeast(0.0)
        _clienteStates.value = _clienteStates.value.toMutableMap().apply { put(clienteId, clienteState) }
    }

    fun confirmarPagamento(clienteId: Long) {
        viewModelScope.launch {
            try {
                val clienteState = _clienteStates.value[clienteId] ?: throw IllegalStateException("Estado do cliente não encontrado")
                val cliente = _clientes.value.find { it.id == clienteId } ?: throw IllegalStateException("Cliente não encontrado")
                val valorPago = clienteState.valorTotal
                val novoValorDevido = (cliente.valorDevido - valorPago).coerceAtLeast(0.0)
                val clienteAtualizado = cliente.copy(valorDevido = novoValorDevido)
                clienteRepository.updateCliente(clienteAtualizado)
                cancelarOperacao(clienteId)
                carregarDados()
            } catch (e: Exception) {
                _error.value = "Erro ao confirmar pagamento: ${e.message}"
            }
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
    private val configuracaoRepository: ConfiguracaoRepository
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
                configuracaoRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}