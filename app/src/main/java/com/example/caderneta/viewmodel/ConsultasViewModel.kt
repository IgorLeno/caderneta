package com.example.caderneta.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.Operacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.OperacaoRepository
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConsultasViewModel(
    private val clienteRepository: ClienteRepository,
    private val vendaRepository: VendaRepository,
    private val localRepository: LocalRepository,
    private val contaRepository: ContaRepository,
    private val operacaoRepository: OperacaoRepository,
    private val configuracoesRepository: ConfiguracoesRepository // Adicionar
) : ViewModel() {

    private val _configuracoes = MutableStateFlow<Configuracoes?>(null)
    private val configuracoes: StateFlow<Configuracoes?> = _configuracoes

    private val _locais = MutableStateFlow<List<Local>>(emptyList())
    val locais: StateFlow<List<Local>> = _locais


    private val _clienteStates = MutableStateFlow<Map<Long, VendasViewModel.ClienteState>>(emptyMap())
    val clienteStates: StateFlow<Map<Long, VendasViewModel.ClienteState>> = _clienteStates

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _localSelecionado = MutableStateFlow<Local?>(null)
    val localSelecionado: StateFlow<Local?> = _localSelecionado

    private val _vendasPorCliente = MutableStateFlow<Map<Long, List<Venda>>>(emptyMap())
    val vendasPorCliente: StateFlow<Map<Long, List<Venda>>> = _vendasPorCliente

    private val _clientesComSaldo = MutableStateFlow<List<Pair<Cliente, Double>>>(emptyList())
    val clientesComSaldo: StateFlow<List<Pair<Cliente, Double>>> = _clientesComSaldo

    private val _searchQuery = MutableStateFlow("")

    private val _saldoAtualizado = MutableSharedFlow<Long>()
    val saldoAtualizado = _saldoAtualizado.asSharedFlow()

    private val _clienteStateUpdates = MutableStateFlow<VendasViewModel.ClienteState?>(null)
    val clienteStateUpdates: StateFlow<VendasViewModel.ClienteState?> = _clienteStateUpdates

    init {
        carregarDados()
    }

    private fun carregarDados() {
        viewModelScope.launch {
            try {
                launch {
                    configuracoesRepository.getConfiguracoes().collect {
                        _configuracoes.value = it
                    }
                }
                carregarLocais()
            } catch (e: Exception) {
                handleError(e)
            }
        }
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



    suspend fun confirmarEdicaoOperacao(vendaOriginal: Venda): Boolean {
        val clienteState = _clienteStates.value[vendaOriginal.clienteId] ?: return false
        val config = _configuracoes.value ?: return false

        try {
            // Calcular saldo a ser ajustado
            val ajusteSaldo = when {
                vendaOriginal.transacao == "a_prazo" &&
                        clienteState.tipoTransacao != TipoTransacao.A_PRAZO -> -vendaOriginal.valor
                vendaOriginal.transacao != "a_prazo" &&
                        clienteState.tipoTransacao == TipoTransacao.A_PRAZO -> clienteState.valorTotal
                vendaOriginal.transacao == "a_prazo" &&
                        clienteState.tipoTransacao == TipoTransacao.A_PRAZO ->
                    clienteState.valorTotal - vendaOriginal.valor
                else -> 0.0
            }

            // Construir detalhes da promoção se aplicável
            val (quantidadeSalgados, quantidadeSucos, promocaoDetalhes) = when {
                clienteState.modoOperacao == ModoOperacao.PROMOCAO -> {
                    val promoDetails = buildPromocaoDetalhes(clienteState, config)
                    val totalSalgados = when {
                        clienteState.quantidadePromo1 > 0 -> config.promo1Salgados * clienteState.quantidadePromo1
                        else -> 0
                    } + when {
                        clienteState.quantidadePromo2 > 0 -> config.promo2Salgados * clienteState.quantidadePromo2
                        else -> 0
                    }
                    val totalSucos = when {
                        clienteState.quantidadePromo1 > 0 -> config.promo1Sucos * clienteState.quantidadePromo1
                        else -> 0
                    } + when {
                        clienteState.quantidadePromo2 > 0 -> config.promo2Sucos * clienteState.quantidadePromo2
                        else -> 0
                    }
                    Triple(totalSalgados, totalSucos, promoDetails)
                }
                else -> Triple(
                    clienteState.quantidadeSalgados,
                    clienteState.quantidadeSucos,
                    null
                )
            }

            // Criar nova venda mantendo os detalhes da promoção
            val novaVenda = vendaOriginal.copy(
                transacao = when (clienteState.tipoTransacao) {
                    TipoTransacao.A_VISTA -> "a_vista"
                    TipoTransacao.A_PRAZO -> "a_prazo"
                    null -> vendaOriginal.transacao
                },
                quantidadeSalgados = quantidadeSalgados,
                quantidadeSucos = quantidadeSucos,
                isPromocao = clienteState.modoOperacao == ModoOperacao.PROMOCAO,
                promocaoDetalhes = promocaoDetalhes,
                valor = clienteState.valorTotal
            )

            // Atualizar venda no banco de dados
            vendaRepository.updateVenda(novaVenda)

            // Atualizar operação correspondente
            operacaoRepository.updateOperacao(
                Operacao(
                    id = vendaOriginal.operacaoId,
                    clienteId = vendaOriginal.clienteId,
                    tipoOperacao = when (clienteState.modoOperacao) {
                        ModoOperacao.VENDA -> "Venda"
                        ModoOperacao.PROMOCAO -> "Promo"
                        ModoOperacao.PAGAMENTO -> "Pagamento"
                        null -> throw IllegalStateException("Modo de operação inválido")
                    },
                    valor = clienteState.valorTotal,
                    data = vendaOriginal.data
                )
            )

            // Atualizar saldo se necessário
            if (ajusteSaldo != 0.0) {
                contaRepository.atualizarSaldo(vendaOriginal.clienteId, ajusteSaldo)
                _saldoAtualizado.emit(vendaOriginal.clienteId)
            }

            // Recarregar vendas do cliente
            carregarVendasPorCliente(vendaOriginal.clienteId)

            return true
        } catch (e: Exception) {
            _error.value = "Erro ao atualizar operação: ${e.message}"
            return false
        }
    }

    private fun buildPromocaoDetalhes(state: VendasViewModel.ClienteState, config: Configuracoes): String {
        val detalhes = mutableListOf<String>()

        if (state.quantidadePromo1 > 0) {
            detalhes.add("${state.quantidadePromo1}x ${config.promo1Nome}")
        }
        if (state.quantidadePromo2 > 0) {
            detalhes.add("${state.quantidadePromo2}x ${config.promo2Nome}")
        }

        return detalhes.joinToString(", ")
    }


    suspend fun atualizarDataVenda(venda: Venda, novaData: java.util.Date): Boolean {
        return try {
            // Atualizar data da venda
            val vendaAtualizada = venda.copy(data = novaData)
            vendaRepository.updateVenda(vendaAtualizada)

            // Atualizar data da operação correspondente
            val operacao = operacaoRepository.getOperacaoById(venda.operacaoId)
            operacao?.let {
                val operacaoAtualizada = it.copy(data = novaData)
                operacaoRepository.updateOperacao(operacaoAtualizada)
            }

            // Recarregar vendas do cliente
            carregarVendasPorCliente(venda.clienteId)
            true
        } catch (e: Exception) {
            _error.value = "Erro ao atualizar data: ${e.message}"
            false
        }
    }

    suspend fun excluirVenda(venda: Venda): Boolean {
        return try {
            // Verificar tipo da transação para ajustar saldo
            when (venda.transacao) {
                "a_prazo" -> {
                    // Se for venda a prazo, diminuir o saldo
                    contaRepository.atualizarSaldo(venda.clienteId, -venda.valor)
                }
                "pagamento" -> {
                    // Se for pagamento, aumentar o saldo
                    contaRepository.atualizarSaldo(venda.clienteId, venda.valor)
                }
            }

            // Excluir venda e operação
            vendaRepository.deleteVenda(venda)
            operacaoRepository.deleteOperacaoById(venda.operacaoId)

            // Recarregar vendas do cliente e emitir atualização de saldo
            carregarVendasPorCliente(venda.clienteId)
            _saldoAtualizado.emit(venda.clienteId)

            true
        } catch (e: Exception) {
            _error.value = "Erro ao excluir operação: ${e.message}"
            false
        }
    }

    private suspend fun OperacaoRepository.getOperacaoById(id: Long): Operacao? {
        return withContext(Dispatchers.IO) {
            // Usar diretamente o DAO através do repository
            operacaoDao.getOperacaoById(id)
        }
    }

    private suspend fun OperacaoRepository.deleteOperacaoById(id: Long) {
        withContext(Dispatchers.IO) {
            getOperacaoById(id)?.let { operacao ->
                // Usar diretamente o DAO através do repository
                operacaoDao.deleteOperacao(operacao)
            }
        }
    }

    fun updateClienteState(state: VendasViewModel.ClienteState) {
        viewModelScope.launch {
            _clienteStates.update { currentStates ->
                currentStates.toMutableMap().apply {
                    put(state.clienteId, state)
                }
            }
        }
    }

    suspend fun abrirEdicaoOperacao(venda: Venda) {
        try {
            // Lógica para preparar edição da operação
            val clienteState = VendasViewModel.ClienteState(
                clienteId = venda.clienteId,
                modoOperacao = when {
                    venda.isPromocao -> ModoOperacao.PROMOCAO
                    venda.transacao == "pagamento" -> ModoOperacao.PAGAMENTO
                    else -> ModoOperacao.VENDA
                },
                tipoTransacao = when (venda.transacao) {
                    "a_vista" -> TipoTransacao.A_VISTA
                    "a_prazo" -> TipoTransacao.A_PRAZO
                    else -> null
                },
                quantidadeSalgados = venda.quantidadeSalgados,
                quantidadeSucos = venda.quantidadeSucos,
                valorTotal = venda.valor
            )

            // Emitir estado para o cliente
            _clienteStateUpdates.emit(clienteState)
        } catch (e: Exception) {
            _error.value = "Erro ao preparar edição: ${e.message}"
        }
    }

    fun clearError() {
        _error.value = null
    }

}

class ConsultasViewModelFactory(
    private val clienteRepository: ClienteRepository,
    private val vendaRepository: VendaRepository,
    private val localRepository: LocalRepository,
    private val contaRepository: ContaRepository,
    private val operacaoRepository: OperacaoRepository,
    private val configuracoesRepository: ConfiguracoesRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConsultasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConsultasViewModel(
                clienteRepository,
                vendaRepository,
                localRepository,
                contaRepository,
                operacaoRepository,
                configuracoesRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}