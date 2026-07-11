package com.example.caderneta.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.domain.FinanceiroService
import com.example.caderneta.domain.foto.ClientePhotoRepository
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository
import com.example.caderneta.util.EspressoIdlingResource
import com.example.caderneta.util.rethrowCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class VendasViewModel(
    private val clienteRepository: ClienteRepository,
    private val localRepository: LocalRepository,
    private val vendaRepository: VendaRepository,
    private val configuracoesRepository: ConfiguracoesRepository,
    private val contaRepository: ContaRepository,
    private val financeiroService: FinanceiroService,
    private val clientePhotoRepository: ClientePhotoRepository? = null,
) : ViewModel() {
    private val _localSelecionado = MutableStateFlow<Local?>(null)
    val localSelecionado: StateFlow<Local?> = _localSelecionado.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    /**
     * Cadeia reativa única: local selecionado + busca -> lista de clientes.
     * flatMapLatest cancela a coleta anterior a cada mudança — não há
     * coletores acumulados. Regra de busca unificada: contém o texto,
     * sem diferenciar maiúsculas (igual em todos os filtros).
     */
    val clientes: StateFlow<List<Cliente>> =
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
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Expansão da árvore de locais é estado de UI, mantido só em memória. */
    private val _locaisExpandidos = MutableStateFlow<Set<Long>>(emptySet())
    private val _filtroLocais = MutableStateFlow("")

    val locais: StateFlow<List<Local>> =
        combine(localRepository.getAllLocais(), _locaisExpandidos, _filtroLocais) { lista, expandidos, filtro ->
            lista
                .filter { local ->
                    !local.arquivado && (filtro.isEmpty() || local.nome.contains(filtro, ignoreCase = true))
                }.map { it.copy(isExpanded = it.id in expandidos) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val configuracoes: StateFlow<Configuracoes?> =
        configuracoesRepository
            .getConfiguracoes()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _clienteStates = MutableStateFlow<Map<Long, ClienteState>>(emptyMap())
    val clienteStates: StateFlow<Map<Long, ClienteState>> = _clienteStates.asStateFlow()

    private val _valorTotal = MutableSharedFlow<Pair<Long, Long>>(extraBufferCapacity = 1)
    val valorTotal: SharedFlow<Pair<Long, Long>> = _valorTotal.asSharedFlow()

    private val _eventos = Channel<UiEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    private val _saldoAtualizado = MutableSharedFlow<Long>()
    val saldoAtualizado = _saldoAtualizado.asSharedFlow()

    private val _clienteStateUpdates = MutableSharedFlow<Long>()
    val clienteStateUpdates = _clienteStateUpdates.asSharedFlow()

    /** Guarda contra confirmação duplicada (duplo clique) por cliente. */
    private val confirmacoesEmAndamento = mutableSetOf<Long>()

    init {
        viewModelScope.launch {
            configuracoes.collect { config ->
                if (config?.promocoesAtivadas == false) {
                    limparEstadosPromocionais()
                }
            }
        }
    }

    // ----- seleção de local e busca -----

    fun selecionarLocal(localId: Long?) {
        viewModelScope.launch {
            try {
                _localSelecionado.value = localId?.let { localRepository.getLocalById(it) }
                _searchQuery.value = ""
            } catch (e: Exception) {
                e.rethrowCancellation()
                publicarErro("Erro ao selecionar local: ${e.message}")
            }
        }
    }

    fun buscarClientes(query: String) {
        _searchQuery.value = query.trim()
    }

    // ----- estado de operação por cliente -----

    fun getClienteState(clienteId: Long): ClienteState? = _clienteStates.value[clienteId]

    fun setInitialState(
        clienteId: Long,
        state: ClienteState,
    ) {
        _clienteStates.update { it + (clienteId to state) }
    }

    fun selecionarModoOperacao(
        cliente: Cliente,
        modoOperacao: ModoOperacao?,
    ) {
        val config = configuracoes.value
        if (modoOperacao in listOf(ModoOperacao.VENDA, ModoOperacao.PROMOCAO) && config == null) {
            publicarErro("Configure os preços em Configurações antes de registrar vendas")
            return
        }
        if (modoOperacao == ModoOperacao.PROMOCAO && config?.promocoesAtivadas != true) {
            publicarErro("Ative as promoções em Configurações antes de registrar promoção")
            return
        }

        val atual = _clienteStates.value[cliente.id]
        val novo =
            if (atual == null || atual.modoOperacao != modoOperacao) {
                ClienteState(clienteId = cliente.id, modoOperacao = modoOperacao)
            } else {
                ClienteState(clienteId = cliente.id)
            }
        atualizarState(novo)
    }

    private fun limparEstadosPromocionais() {
        val estadosPromocionais = _clienteStates.value.values.filter { it.modoOperacao == ModoOperacao.PROMOCAO }
        if (estadosPromocionais.isEmpty()) return

        _clienteStates.update { estados ->
            estados + estadosPromocionais.associate { state -> state.clienteId to ClienteState(state.clienteId) }
        }
        estadosPromocionais.forEach { state ->
            _valorTotal.tryEmit(state.clienteId to 0L)
            viewModelScope.launch { _clienteStateUpdates.emit(state.clienteId) }
        }
    }

    fun selecionarTipoTransacao(
        cliente: Cliente,
        tipoTransacao: TipoTransacao?,
    ) {
        val atual = _clienteStates.value[cliente.id] ?: return
        val novoTipo = if (atual.tipoTransacao == tipoTransacao) null else tipoTransacao
        atualizarState(
            atual.copy(
                tipoTransacao = novoTipo,
                quantidadeSalgados = 0,
                quantidadeSucos = 0,
                quantidadePromo1 = 0,
                quantidadePromo2 = 0,
                valorTotalCentavos = 0,
            ),
        )
    }

    fun updateQuantidadeSalgados(
        clienteId: Long,
        quantidade: Int,
    ) {
        _clienteStates.value[clienteId]?.let {
            atualizarState(it.copy(quantidadeSalgados = quantidade))
        }
    }

    fun updateQuantidadeSucos(
        clienteId: Long,
        quantidade: Int,
    ) {
        _clienteStates.value[clienteId]?.let {
            atualizarState(it.copy(quantidadeSucos = quantidade))
        }
    }

    fun updateQuantidadePromo1(
        clienteId: Long,
        quantidade: Int,
    ) {
        _clienteStates.value[clienteId]?.let {
            atualizarState(it.copy(quantidadePromo1 = quantidade))
        }
    }

    fun updateQuantidadePromo2(
        clienteId: Long,
        quantidade: Int,
    ) {
        _clienteStates.value[clienteId]?.let {
            atualizarState(it.copy(quantidadePromo2 = quantidade))
        }
    }

    /** Valor digitado manualmente (pagamento), em centavos. */
    fun updateValorTotal(
        clienteId: Long,
        valorCentavos: Long,
    ) {
        val atual = _clienteStates.value[clienteId] ?: ClienteState(clienteId = clienteId)
        val novo = atual.copy(valorTotalCentavos = valorCentavos)
        _clienteStates.update { it + (clienteId to novo) }
        _valorTotal.tryEmit(clienteId to valorCentavos)
    }

    /**
     * Recalcula o valor e publica o novo estado. Para PAGAMENTO o valor é o
     * digitado; para venda/promoção deriva de quantidades e configurações.
     */
    private fun atualizarState(state: ClienteState) {
        val config = configuracoes.value
        val valor =
            when (state.modoOperacao) {
                ModoOperacao.VENDA -> config?.let { state.calcularValorVendaNormal(it) } ?: 0
                ModoOperacao.PROMOCAO -> config?.let { state.calcularValorPromocional(it) } ?: 0
                ModoOperacao.PAGAMENTO -> state.valorTotalCentavos
                null -> 0
            }
        val novo = state.copy(valorTotalCentavos = valor)
        _clienteStates.update { it + (state.clienteId to novo) }
        _valorTotal.tryEmit(state.clienteId to valor)
    }

    /** Recalcula a partir do estado atual (usado pela edição de operação). */
    fun recalcularValorTotal(clienteId: Long) {
        _clienteStates.value[clienteId]?.let { atualizarState(it) }
    }

    // ----- confirmação de operações (delegada ao FinanceiroService) -----

    fun confirmarOperacao(clienteId: Long) {
        val clienteState = _clienteStates.value[clienteId] ?: return
        if (!confirmacoesEmAndamento.add(clienteId)) return
        EspressoIdlingResource.increment()
        viewModelScope.launch {
            try {
                when (clienteState.modoOperacao) {
                    ModoOperacao.VENDA, ModoOperacao.PROMOCAO -> confirmarVenda(clienteState)
                    ModoOperacao.PAGAMENTO -> confirmarPagamento(clienteState)
                    null -> {}
                }
            } finally {
                confirmacoesEmAndamento.remove(clienteId)
                EspressoIdlingResource.decrement()
            }
        }
    }

    private suspend fun confirmarVenda(clienteState: ClienteState) {
        try {
            val localId =
                checkNotNull(_localSelecionado.value?.id) {
                    "Selecione um local antes de registrar a venda"
                }
            val config =
                checkNotNull(configuracoes.value) {
                    "Configure os preços antes de registrar vendas"
                }
            val tipoTransacao =
                checkNotNull(clienteState.tipoTransacao) {
                    "Selecione à vista ou a prazo"
                }

            val (quantidadeSalgados, quantidadeSucos) = clienteState.calcularQuantidadesTotais(config)
            val valorCentavos =
                if (clienteState.isPromocao()) {
                    clienteState.calcularValorPromocional(config)
                } else {
                    clienteState.calcularValorVendaNormal(config)
                }

            financeiroService.registrarVenda(
                clienteId = clienteState.clienteId,
                localId = localId,
                tipoTransacao = tipoTransacao,
                isPromocao = clienteState.isPromocao(),
                quantidadeSalgados = quantidadeSalgados,
                quantidadeSucos = quantidadeSucos,
                valorCentavos = valorCentavos,
                promocaoDetalhes =
                    if (clienteState.isPromocao()) {
                        clienteState.montarPromocaoDetalhes(config)
                    } else {
                        null
                    },
            )

            if (tipoTransacao == TipoTransacao.A_PRAZO) {
                _saldoAtualizado.emit(clienteState.clienteId)
            }
            resetClienteState(clienteState.clienteId)
            _eventos.send(UiEvento.Sucesso("Venda registrada com sucesso!"))
        } catch (e: Exception) {
            e.rethrowCancellation()
            publicarErro("Erro ao confirmar venda: ${e.message}")
        }
    }

    private suspend fun confirmarPagamento(clienteState: ClienteState) {
        try {
            financeiroService.registrarPagamento(
                clienteId = clienteState.clienteId,
                localId = _localSelecionado.value?.id,
                valorCentavos = clienteState.valorTotalCentavos,
            )
            _saldoAtualizado.emit(clienteState.clienteId)
            resetClienteState(clienteState.clienteId)
            _eventos.send(UiEvento.Sucesso("Pagamento registrado com sucesso!"))
        } catch (e: Exception) {
            e.rethrowCancellation()
            publicarErro("Erro ao confirmar pagamento: ${e.message}")
        }
    }

    fun cancelarOperacao(clienteId: Long) {
        resetClienteState(clienteId)
    }

    private fun resetClienteState(clienteId: Long) {
        _clienteStates.update { it + (clienteId to ClienteState(clienteId = clienteId)) }
        _valorTotal.tryEmit(clienteId to 0L)
        viewModelScope.launch { _clienteStateUpdates.emit(clienteId) }
    }

    private fun publicarErro(mensagem: String) {
        viewModelScope.launch { _eventos.send(UiEvento.Erro(mensagem)) }
    }

    // ----- locais -----

    fun addLocal(
        nome: String,
        parentId: Long? = null,
    ) {
        viewModelScope.launch {
            try {
                val parentLevel = parentId?.let { localRepository.getLocalById(it)?.level ?: -1 } ?: -1
                localRepository.insertLocal(
                    Local(nome = nome, endereco = "", parentId = parentId, level = parentLevel + 1),
                )
            } catch (e: Exception) {
                e.rethrowCancellation()
                publicarErro("Erro ao adicionar local: ${e.message}")
            }
        }
    }

    fun editLocal(local: Local) {
        viewModelScope.launch {
            try {
                localRepository.updateLocal(local)
            } catch (e: Exception) {
                e.rethrowCancellation()
                publicarErro("Erro ao editar local: ${e.message}")
            }
        }
    }

    /**
     * Local com clientes/vendas/sublocais não pode ser apagado (FK RESTRICT):
     * nesse caso é arquivado, preservando o histórico.
     */
    fun deleteLocal(local: Local) {
        viewModelScope.launch {
            try {
                val arquivado = localRepository.deleteLocal(local)
                if (arquivado) {
                    publicarErro("${local.nome} possui registros e foi arquivado em vez de excluído")
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                publicarErro("Erro ao deletar local: ${e.message}")
            }
        }
    }

    /** A lista de locais é reativa; método mantido por compatibilidade. */
    fun reloadLocais() {
        _filtroLocais.value = ""
    }

    fun searchLocais(query: String) {
        _filtroLocais.value = query.trim()
    }

    fun toggleLocalExpansion(local: Local) {
        _locaisExpandidos.update { atual ->
            if (local.id in atual) atual - local.id else atual + local.id
        }
    }

    fun getSublocais(parentId: Long?): List<Local> = locais.value.filter { it.parentId == parentId }

    fun getLocalHierarchy(localId: Long): List<Local> {
        val hierarchy = mutableListOf<Local>()
        var currentId: Long? = localId

        while (currentId != null) {
            val currentLocal = locais.value.find { it.id == currentId } ?: break
            hierarchy.add(0, currentLocal)
            currentId = currentLocal.parentId
        }

        if (hierarchy.zipWithNext().any { (parent, child) -> child.level <= parent.level }) {
            return emptyList()
        }

        return hierarchy
    }

    // ----- clientes -----

    @Suppress("LongParameterList")
    fun addCliente(
        nome: String,
        telefone: String,
        localId: Long,
        sublocal1Id: Long?,
        sublocal2Id: Long?,
        sublocal3Id: Long?,
        fotoUri: Uri? = null,
    ) {
        viewModelScope.launch {
            try {
                validarHierarquia(localId, sublocal1Id, sublocal2Id, sublocal3Id)
                val clienteId =
                    clienteRepository.insertCliente(
                        Cliente(
                            nome = nome,
                            telefone = telefone,
                            localId = localId,
                            sublocal1Id = sublocal1Id,
                            sublocal2Id = sublocal2Id,
                            sublocal3Id = sublocal3Id,
                        ),
                    )
                fotoUri?.let { uri -> clientePhotoRepository?.salvarFotoCliente(clienteId, uri) }
            } catch (e: Exception) {
                e.rethrowCancellation()
                publicarErro("Erro ao adicionar cliente: ${e.message}")
            }
        }
    }

    @Suppress("LongParameterList")
    fun editarCliente(
        cliente: Cliente,
        novoNome: String,
        novoTelefone: String,
        novoLocalId: Long,
        novoSublocal1Id: Long?,
        novoSublocal2Id: Long?,
        novoSublocal3Id: Long?,
        fotoUri: Uri? = null,
        removerFoto: Boolean = false,
    ) {
        viewModelScope.launch {
            try {
                validarHierarquia(novoLocalId, novoSublocal1Id, novoSublocal2Id, novoSublocal3Id)
                clienteRepository.updateCliente(
                    cliente.copy(
                        nome = novoNome,
                        telefone = novoTelefone,
                        localId = novoLocalId,
                        sublocal1Id = novoSublocal1Id,
                        sublocal2Id = novoSublocal2Id,
                        sublocal3Id = novoSublocal3Id,
                    ),
                )
                when {
                    removerFoto -> clientePhotoRepository?.removerFoto(cliente.id)
                    fotoUri != null -> clientePhotoRepository?.salvarFotoCliente(cliente.id, fotoUri)
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                publicarErro("Erro ao atualizar cliente: ${e.message}")
            }
        }
    }

    fun arquivoFotoCliente(fotoNome: String?): File? = clientePhotoRepository?.arquivo(fotoNome)

    /**
     * Cliente com histórico financeiro não pode ser apagado (FK RESTRICT):
     * nesse caso é arquivado, preservando vendas e extrato.
     */
    fun excluirCliente(cliente: Cliente) {
        viewModelScope.launch {
            try {
                val arquivado = clienteRepository.deleteCliente(cliente)
                if (arquivado) {
                    publicarErro("${cliente.nome} possui histórico e foi arquivado em vez de excluído")
                } else {
                    clientePhotoRepository?.deleteFile(cliente.fotoNome)
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                publicarErro("Erro ao excluir cliente: ${e.message}")
            }
        }
    }

    private suspend fun validarHierarquia(
        localId: Long,
        sublocal1Id: Long?,
        sublocal2Id: Long?,
        sublocal3Id: Long?,
    ) {
        val ids = listOfNotNull(localId, sublocal1Id, sublocal2Id, sublocal3Id)
        val hierarquia =
            ids.map { id ->
                checkNotNull(localRepository.getLocalById(id)) {
                    "Local da hierarquia não encontrado"
                }
            }
        check(hierarquia.none { it.arquivado }) { "Hierarquia contém local arquivado" }
        hierarquia.zipWithNext().forEach { (parent, child) ->
            check(child.level > parent.level) { "Hierarquia de locais inválida" }
            check(child.parentId == parent.id) { "Hierarquia de locais inválida" }
        }
    }
}

class VendasViewModelFactory(
    private val clienteRepository: ClienteRepository,
    private val localRepository: LocalRepository,
    private val vendaRepository: VendaRepository,
    private val configuracoesRepository: ConfiguracoesRepository,
    private val contaRepository: ContaRepository,
    private val financeiroService: FinanceiroService,
    private val clientePhotoRepository: ClientePhotoRepository? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VendasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VendasViewModel(
                clienteRepository,
                localRepository,
                vendaRepository,
                configuracoesRepository,
                contaRepository,
                financeiroService,
                clientePhotoRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
