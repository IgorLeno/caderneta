package com.example.caderneta.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.domain.FinanceiroService
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Tela de consultas: lista de clientes com saldo e extrato por cliente.
 * Tudo reativo: saldos vêm exclusivamente da tabela `contas` (cache mantido
 * pelo FinanceiroService) — esta camada nunca recalcula nem regrava saldo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConsultasViewModel(
    private val clienteRepository: ClienteRepository,
    private val vendaRepository: VendaRepository,
    private val localRepository: LocalRepository,
    private val contaRepository: ContaRepository,
    private val configuracoesRepository: ConfiguracoesRepository,
    private val financeiroService: FinanceiroService,
) : ViewModel() {
    val configuracoes: StateFlow<Configuracoes?> =
        configuracoesRepository
            .getConfiguracoes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _localSelecionado = MutableStateFlow<Local?>(null)
    val localSelecionado: StateFlow<Local?> = _localSelecionado.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private val _locaisExpandidos = MutableStateFlow<Set<Long>>(emptySet())
    private val _filtroLocais = MutableStateFlow("")

    val locais: StateFlow<List<Local>> =
        combine(localRepository.getAllLocais(), _locaisExpandidos, _filtroLocais) { lista, expandidos, filtro ->
            lista
                .filter { local ->
                    !local.arquivado && (filtro.isEmpty() || local.nome.contains(filtro, ignoreCase = true))
                }.map { it.copy(isExpanded = it.id in expandidos) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val clientesFiltrados: Flow<List<Cliente>> =
        combine(_localSelecionado, _searchQuery) { local, query -> local to query }
            .flatMapLatest { (local, query) ->
                val base =
                    if (local == null) {
                        clienteRepository.getAllClientes()
                    } else {
                        clienteRepository.getClientesByLocalHierarchy(local.id)
                    }
                base.map { lista ->
                    lista.filter { cliente ->
                        !cliente.arquivado &&
                            (query.isEmpty() || cliente.nome.contains(query, ignoreCase = true))
                    }
                }
            }

    /** Clientes com saldo (centavos), sempre em sincronia com a tabela contas. */
    val clientesComSaldo: StateFlow<List<Pair<Cliente, Long>>> =
        combine(clientesFiltrados, contaRepository.observeAllContas()) { clientes, contas ->
            val saldoPorCliente = contas.associate { it.clienteId to it.saldoCentavos }
            clientes.map { it to (saldoPorCliente[it.id] ?: 0L) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Clientes com extrato aberto; as vendas de cada um são observadas do banco. */
    private val _clientesComExtratoAberto = MutableStateFlow<Set<Long>>(emptySet())

    val vendasPorCliente: StateFlow<Map<Long, List<Venda>>> =
        _clientesComExtratoAberto
            .flatMapLatest { ids ->
                if (ids.isEmpty()) {
                    flowOf(emptyMap())
                } else {
                    combine(
                        ids.map { id -> vendaRepository.getVendasByCliente(id).map { id to it } },
                    ) { pares -> pares.toMap() }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _clienteStates = MutableStateFlow<Map<Long, ClienteState>>(emptyMap())
    val clienteStates: StateFlow<Map<Long, ClienteState>> = _clienteStates.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saldoAtualizado = MutableSharedFlow<Long>()
    val saldoAtualizado = _saldoAtualizado.asSharedFlow()

    // ----- seleção e busca -----

    fun selecionarLocal(localId: Long?) {
        viewModelScope.launch {
            try {
                _localSelecionado.value = localId?.let { localRepository.getLocalById(it) }
                _searchQuery.value = ""
            } catch (e: Exception) {
                _error.value = "Erro ao selecionar local: ${e.message}"
            }
        }
    }

    fun buscarClientes(query: String) {
        _searchQuery.value = query.trim()
    }

    fun buscarLocais(query: String) {
        _filtroLocais.value = query.trim()
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun toggleLocalExpansion(local: Local) {
        _locaisExpandidos.update { atual ->
            if (local.id in atual) atual - local.id else atual + local.id
        }
    }

    // ----- extrato -----

    fun carregarVendasPorCliente(clienteId: Long) {
        _clientesComExtratoAberto.update { it + clienteId }
    }

    suspend fun getSaldoCliente(clienteId: Long): Long = contaRepository.getSaldoCentavos(clienteId)

    // ----- edição de lançamentos (delegada ao FinanceiroService) -----

    fun updateClienteState(state: ClienteState) {
        val ajustado =
            if (state.modoOperacao == com.example.caderneta.data.entity.ModoOperacao.PAGAMENTO) {
                state.copy(valorTotalCentavos = state.valorTotalCentavos.coerceAtLeast(0))
            } else {
                state
            }
        _clienteStates.update { it + (ajustado.clienteId to ajustado) }
    }

    suspend fun confirmarEdicaoOperacao(vendaOriginal: Venda): Boolean {
        val clienteState = _clienteStates.value[vendaOriginal.clienteId] ?: return false
        return try {
            val vendaEditada =
                if (vendaOriginal.transacao == TransacaoVenda.PAGAMENTO) {
                    vendaOriginal.copy(valorCentavos = clienteState.valorTotalCentavos)
                } else {
                    val config =
                        checkNotNull(configuracoes.value) {
                            "Configurações não carregadas"
                        }
                    val (quantidadeSalgados, quantidadeSucos) = clienteState.calcularQuantidadesTotais(config)
                    vendaOriginal.copy(
                        transacao =
                            when (clienteState.tipoTransacao) {
                                TipoTransacao.A_VISTA -> TransacaoVenda.A_VISTA
                                TipoTransacao.A_PRAZO -> TransacaoVenda.A_PRAZO
                                null -> error("Selecione à vista ou a prazo")
                            },
                        quantidadeSalgados = quantidadeSalgados,
                        quantidadeSucos = quantidadeSucos,
                        isPromocao = clienteState.isPromocao(),
                        promocaoDetalhes =
                            if (clienteState.isPromocao()) {
                                clienteState.montarPromocaoDetalhes(config)
                            } else {
                                null
                            },
                        valorCentavos = clienteState.valorTotalCentavos,
                    )
                }

            financeiroService.editarOperacao(vendaOriginal, vendaEditada)
            _saldoAtualizado.emit(vendaOriginal.clienteId)
            true
        } catch (e: Exception) {
            _error.value = "Erro ao atualizar operação: ${e.message}"
            false
        }
    }

    suspend fun atualizarDataVenda(
        venda: Venda,
        novaData: Date,
    ): Boolean =
        try {
            financeiroService.atualizarDataOperacao(venda, novaData)
            true
        } catch (e: Exception) {
            _error.value = "Erro ao atualizar data: ${e.message}"
            false
        }

    suspend fun excluirVenda(venda: Venda): Boolean =
        try {
            financeiroService.excluirOperacao(venda)
            _saldoAtualizado.emit(venda.clienteId)
            true
        } catch (e: Exception) {
            _error.value = "Erro ao excluir operação: ${e.message}"
            false
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
    private val configuracoesRepository: ConfiguracoesRepository,
    private val financeiroService: FinanceiroService,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConsultasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConsultasViewModel(
                clienteRepository,
                vendaRepository,
                localRepository,
                contaRepository,
                configuracoesRepository,
                financeiroService,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
