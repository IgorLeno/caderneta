package com.example.caderneta.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.*
import com.example.caderneta.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _saldoAtualizado = MutableSharedFlow<Long>()
    val saldoAtualizado = _saldoAtualizado.asSharedFlow()

    private val _clienteStateUpdates = MutableSharedFlow<Long>()
    val clienteStateUpdates = _clienteStateUpdates.asSharedFlow()

    private val _operacaoConfirmada = MutableStateFlow<OperacaoConfirmada?>(null)
    val operacaoConfirmada: StateFlow<OperacaoConfirmada?> = _operacaoConfirmada.asStateFlow()

    init {
        carregarDados()
    }

    data class ClienteState(
        val clienteId: Long,
        var modoOperacao: ModoOperacao? = null,
        var tipoTransacao: TipoTransacao? = null,
        var quantidadeSalgados: Int = 0,
        var quantidadeSucos: Int = 0,
        var quantidadePromo1: Int = 0,
        var quantidadePromo2: Int = 0,
        var valorTotal: Double = 0.0
    ) {
        fun resetQuantidades() {
            quantidadeSalgados = 0
            quantidadeSucos = 0
            quantidadePromo1 = 0
            quantidadePromo2 = 0
            valorTotal = 0.0
        }

        fun isPromocao() = modoOperacao == ModoOperacao.PROMOCAO
    }

    sealed class OperacaoConfirmada {
        object Venda : OperacaoConfirmada()
        object Pagamento : OperacaoConfirmada()
    }

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
                val local = localRepository.getLocalById(localId)
                _localSelecionado.value = local

                Log.d("VendasViewModel", """
                === Selecionando Local ===
                Local: ${local?.nome} (ID: $localId)
            """.trimIndent())

                clienteRepository.getClientesByLocalHierarchy(localId).collect { clientesFiltrados ->
                    Log.d("VendasViewModel", "Clientes filtrados para local $localId: ${clientesFiltrados.size}")
                    _clientes.value = clientesFiltrados
                    logHieraquiaClientes(localId, clientesFiltrados)
                }
            } catch (e: Exception) {
                Log.e("VendasViewModel", "Erro ao selecionar local", e)
                _error.value = "Erro ao selecionar local: ${e.message}"
            }
        }
    }


    fun getClienteState(clienteId: Long): ClienteState? {
        return _clienteStates.value[clienteId]
    }

    fun editarCliente(
        cliente: Cliente,
        novoNome: String,
        novoTelefone: String,
        novoLocalId: Long,
        novoSublocal1Id: Long?,
        novoSublocal2Id: Long?,
        novoSublocal3Id: Long?
    ) {
        viewModelScope.launch {
            try {
                val clienteAtualizado = cliente.copy(
                    nome = novoNome,
                    telefone = novoTelefone,
                    localId = novoLocalId,
                    sublocal1Id = novoSublocal1Id,
                    sublocal2Id = novoSublocal2Id,
                    sublocal3Id = novoSublocal3Id
                )

                // Validar hierarquia de locais
                val locais = listOfNotNull(
                    localRepository.getLocalById(novoLocalId),
                    novoSublocal1Id?.let { localRepository.getLocalById(it) },
                    novoSublocal2Id?.let { localRepository.getLocalById(it) },
                    novoSublocal3Id?.let { localRepository.getLocalById(it) }
                )

                // Validar níveis da hierarquia
                if (locais.zipWithNext().any { (parent, child) ->
                        child.level <= parent.level
                    }) {
                    throw IllegalStateException("Hierarquia de locais inválida")
                }

                clienteRepository.updateCliente(clienteAtualizado)

                // Recarregar clientes do local atual
                _localSelecionado.value?.let { local ->
                    clienteRepository.getClientesByLocalHierarchy(local.id).collect { clientes ->
                        _clientes.value = clientes
                    }
                }
            } catch (e: Exception) {
                _error.value = "Erro ao atualizar cliente: ${e.message}"
            }
        }
    }

    fun excluirCliente(cliente: Cliente) {
        viewModelScope.launch {
            try {
                clienteRepository.deleteCliente(cliente)

                // Recarregar lista de clientes
                _localSelecionado.value?.let { local ->
                    clienteRepository.getClientesByLocalHierarchy(local.id).collect { clientes ->
                        _clientes.value = clientes
                    }
                }
            } catch (e: Exception) {
                _error.value = "Erro ao excluir cliente: ${e.message}"
            }
        }
    }

    fun setInitialState(clienteId: Long, state: ClienteState) {
        viewModelScope.launch {
            _clienteStates.update { currentStates ->
                currentStates.toMutableMap().apply {
                    put(clienteId, state)
                }
            }
        }
    }



    suspend fun confirmarEdicaoOperacao(vendaOriginal: Venda): Boolean {
        val clienteState = _clienteStates.value[vendaOriginal.clienteId] ?: return false

        try {
            // Calcular saldo a ser ajustado
            val ajusteSaldo = when {
                // Se a operação original era a prazo e a nova não é, diminuir saldo antigo
                vendaOriginal.transacao == "a_prazo" &&
                        clienteState.tipoTransacao != TipoTransacao.A_PRAZO -> -vendaOriginal.valor

                // Se a operação original não era a prazo e a nova é, aumentar com novo valor
                vendaOriginal.transacao != "a_prazo" &&
                        clienteState.tipoTransacao == TipoTransacao.A_PRAZO -> clienteState.valorTotal

                // Se ambas são a prazo, ajustar a diferença
                vendaOriginal.transacao == "a_prazo" &&
                        clienteState.tipoTransacao == TipoTransacao.A_PRAZO ->
                    clienteState.valorTotal - vendaOriginal.valor

                else -> 0.0
            }

            // Criar nova venda baseada no estado atual
            val novaVenda = vendaOriginal.copy(
                transacao = when (clienteState.tipoTransacao) {
                    TipoTransacao.A_VISTA -> "a_vista"
                    TipoTransacao.A_PRAZO -> "a_prazo"
                    null -> "pagamento"
                },
                quantidadeSalgados = clienteState.quantidadeSalgados,
                quantidadeSucos = clienteState.quantidadeSucos,
                isPromocao = clienteState.modoOperacao == ModoOperacao.PROMOCAO,
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

            return true
        } catch (e: Exception) {
            _error.value = "Erro ao atualizar operação: ${e.message}"
            return false
        }
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

    fun updateQuantidadePromo1(clienteId: Long, quantidade: Int) {
        val clienteState = getClienteState(clienteId) ?: return
        clienteState.quantidadePromo1 = quantidade
        calcularValorTotal(clienteState)
    }

    fun updateQuantidadePromo2(clienteId: Long, quantidade: Int) {
        val clienteState = getClienteState(clienteId) ?: return
        clienteState.quantidadePromo2 = quantidade
        calcularValorTotal(clienteState)
    }

    fun calcularValorTotal(state: ClienteState) {
        val config = _configuracoes.value ?: return

        val novoValor = when (state.modoOperacao) {
            ModoOperacao.VENDA -> calcularValorVendaNormal(state, config)
            ModoOperacao.PROMOCAO -> calcularValorVendaPromocional(state, config)
            ModoOperacao.PAGAMENTO -> state.valorTotal
            null -> 0.0
        }

        state.valorTotal = novoValor

        viewModelScope.launch {
            _valorTotal.emit(state.clienteId to novoValor)
            _clienteStates.update { currentStates ->
                currentStates.toMutableMap().apply {
                    put(state.clienteId, state)
                }
            }
        }
    }

    private fun calcularValorVendaNormal(state: ClienteState, config: Configuracoes): Double {
        val tipoTransacao = state.tipoTransacao ?: return 0.0

        return when (tipoTransacao) {
            TipoTransacao.A_VISTA -> {
                (state.quantidadeSalgados * config.precoSalgadoVista) +
                        (state.quantidadeSucos * config.precoSucoVista)
            }
            TipoTransacao.A_PRAZO -> {
                (state.quantidadeSalgados * config.precoSalgadoPrazo) +
                        (state.quantidadeSucos * config.precoSucoPrazo)
            }
        }
    }

    private fun calcularValorVendaPromocional(state: ClienteState, config: Configuracoes): Double {
        if (!config.promocoesAtivadas) return 0.0

        val tipoTransacao = state.tipoTransacao ?: return 0.0

        val valorPromo1 = if (state.quantidadePromo1 > 0) {
            config.calcularValorPromocao(1, state.quantidadePromo1, tipoTransacao)
        } else 0.0

        val valorPromo2 = if (state.quantidadePromo2 > 0) {
            config.calcularValorPromocao(2, state.quantidadePromo2, tipoTransacao)
        } else 0.0

        return valorPromo1 + valorPromo2
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
                val clienteState = _clienteStates.value[clienteId]
                    ?: throw IllegalStateException("Estado do cliente não encontrado")
                val localId = _localSelecionado.value?.id
                    ?: throw IllegalStateException("Local não selecionado")
                val config = _configuracoes.value
                    ?: throw IllegalStateException("Configurações não encontradas")

                val tipoTransacao = clienteState.tipoTransacao
                    ?: throw IllegalStateException("Tipo de transação não selecionado")

                // Calcula as quantidades totais
                val (quantidadeSalgadosFinal, quantidadeSucosFinal) = calcularQuantidades(clienteState, config)

                // Calcula o valor total
                val valorTotal = if (clienteState.isPromocao()) {
                    calcularValorVendaPromocional(clienteState, config)
                } else {
                    calcularValorVendaNormal(clienteState, config)
                }

                // Registra operação e venda
                registrarOperacaoEVenda(
                    clienteId = clienteId,
                    localId = localId,
                    clienteState = clienteState,
                    valorTotal = valorTotal,
                    quantidadeSalgados = quantidadeSalgadosFinal,
                    quantidadeSucos = quantidadeSucosFinal,
                    config = config,
                    tipoTransacao = tipoTransacao
                )

                // Atualiza saldo se for venda a prazo
                if (tipoTransacao == TipoTransacao.A_PRAZO) {
                    atualizarSaldoVendaPrazo(clienteId, valorTotal)
                }

                resetClienteState(clienteId)
                _operacaoConfirmada.value = OperacaoConfirmada.Venda

            } catch (e: Exception) {
                _error.value = "Erro ao confirmar venda: ${e.message}"
            }
        }
    }

    private fun calcularQuantidades(
        clienteState: ClienteState,
        config: Configuracoes
    ): Pair<Int, Int> {
        return if (clienteState.isPromocao()) {
            val quant1 = config.calcularQuantidadesPromocao(1, clienteState.quantidadePromo1)
            val quant2 = config.calcularQuantidadesPromocao(2, clienteState.quantidadePromo2)
            Pair(
                quant1.salgados + quant2.salgados,
                quant1.sucos + quant2.sucos
            )
        } else {
            Pair(clienteState.quantidadeSalgados, clienteState.quantidadeSucos)
        }
    }

    private suspend fun registrarOperacaoEVenda(
        clienteId: Long,
        localId: Long,
        clienteState: ClienteState,
        valorTotal: Double,
        quantidadeSalgados: Int,
        quantidadeSucos: Int,
        config: Configuracoes,
        tipoTransacao: TipoTransacao
    ) {
        val operacao = Operacao(
            clienteId = clienteId,
            tipoOperacao = if (clienteState.isPromocao()) "Promo" else "Venda",
            valor = valorTotal,
            data = Date()
        )
        val operacaoId = operacaoRepository.insertOperacao(operacao)

        val venda = Venda(
            operacaoId = operacaoId,
            clienteId = clienteId,
            localId = localId,
            data = Date(),
            transacao = when (tipoTransacao) {
                TipoTransacao.A_VISTA -> "a_vista"
                TipoTransacao.A_PRAZO -> "a_prazo"
            },
            quantidadeSalgados = quantidadeSalgados,
            quantidadeSucos = quantidadeSucos,
            valor = valorTotal,
            isPromocao = clienteState.isPromocao(),
            promocaoDetalhes = if (clienteState.isPromocao()) {
                buildPromocaoDetalhes(clienteState, config)
            } else null
        )
        vendaRepository.insertVenda(venda)
    }

    private suspend fun atualizarSaldoVendaPrazo(clienteId: Long, valorTotal: Double) {
        withContext(Dispatchers.IO) {
            val conta = contaRepository.getContaByCliente(clienteId)
            if (conta == null) {
                contaRepository.insertConta(Conta(clienteId = clienteId, saldo = valorTotal))
            } else {
                contaRepository.atualizarSaldo(clienteId, valorTotal)
            }
        }
        _saldoAtualizado.emit(clienteId)
    }

    private fun buildPromocaoDetalhes(state: ClienteState, config: Configuracoes): String {
        val detalhes = mutableListOf<String>()

        if (state.quantidadePromo1 > 0) {
            detalhes.add("${state.quantidadePromo1}x ${config.promo1Nome}")
        }
        if (state.quantidadePromo2 > 0) {
            detalhes.add("${state.quantidadePromo2}x ${config.promo2Nome}")
        }

        return detalhes.joinToString(", ")
    }

    private fun confirmarPagamento(clienteId: Long) {
        viewModelScope.launch {
            try {
                val clienteState = _clienteStates.value[clienteId]
                    ?: throw IllegalStateException("Estado do cliente não encontrado")
                val valorPagamento = clienteState.valorTotal

                Log.d("VendasViewModel", "Tentando confirmar pagamento: clienteId=$clienteId, valor=$valorPagamento")

                if (valorPagamento <= 0) {
                    throw IllegalStateException("Valor do pagamento inválido")
                }

                // Verifica se tem saldo suficiente
                val conta = contaRepository.getContaByCliente(clienteId)
                if (conta == null || valorPagamento > conta.saldo) {
                    throw IllegalStateException("Valor do pagamento maior que o saldo devedor")
                }

                // Processa o pagamento
                val pagamentoProcessado = withContext(Dispatchers.IO) {
                    contaRepository.processarPagamento(clienteId, valorPagamento)
                }

                if (!pagamentoProcessado) {
                    throw IllegalStateException("Erro ao processar pagamento")
                }

                // Registra operação e pagamento
                withContext(Dispatchers.IO) {
                    registrarOperacaoEPagamento(clienteId, valorPagamento)
                }

                // Atualiza UI
                withContext(Dispatchers.Main) {
                    _saldoAtualizado.emit(clienteId)
                    resetClienteState(clienteId)
                    _operacaoConfirmada.value = OperacaoConfirmada.Pagamento
                }

            } catch (e: Exception) {
                Log.e("VendasViewModel", "Erro ao confirmar pagamento", e)
                _error.value = "Erro ao confirmar pagamento: ${e.message}"
            }
        }
    }

    private suspend fun registrarOperacaoEPagamento(clienteId: Long, valorPagamento: Double) {
        val operacao = Operacao(
            clienteId = clienteId,
            tipoOperacao = "Pagamento",
            valor = valorPagamento,
            data = Date()
        )
        val operacaoId = operacaoRepository.insertOperacao(operacao)

        val venda = Venda(
            operacaoId = operacaoId,
            clienteId = clienteId,
            localId = _localSelecionado.value?.id ?: 0,
            data = Date(),
            transacao = "pagamento",
            quantidadeSalgados = 0,
            quantidadeSucos = 0,
            valor = valorPagamento
        )
        vendaRepository.insertVenda(venda)
    }

    fun updateValorTotal(clienteId: Long, valor: Double) {
        viewModelScope.launch {
            _clienteStates.update { currentStates ->
                val currentState = currentStates[clienteId] ?: ClienteState(clienteId = clienteId)
                val newState = currentState.copy(valorTotal = valor)
                currentStates + (clienteId to newState)
            }
            _valorTotal.emit(clienteId to valor)
        }
    }

    fun cancelarOperacao(clienteId: Long) {
        resetClienteState(clienteId)
    }

    private fun resetClienteState(clienteId: Long) {
        viewModelScope.launch(Dispatchers.Main) {
            _clienteStates.update { currentStates ->
                currentStates.toMutableMap().apply {
                    put(clienteId, ClienteState(clienteId = clienteId))
                }
            }
            _valorTotal.emit(clienteId to 0.0)
            delay(100) // Pequeno delay para garantir que a UI esteja pronta
            _clienteStateUpdates.emit(clienteId)
        }
    }

    fun resetOperacaoConfirmada() {
        _operacaoConfirmada.value = null
    }

    fun clearError() {
        _error.value = null
    }

    // Funções relacionadas a Locais
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
            } catch (e: Exception) {
                _error.value = "Erro ao alternar expansão do local: ${e.message}"
            }
        }
    }

    fun getSublocais(parentId: Long?): List<Local> {
        return _locais.value.filter { it.parentId == parentId }
    }

    fun getLocalHierarchy(localId: Long): List<Local> {
        val hierarchy = mutableListOf<Local>()
        var currentId: Long? = localId

        while (currentId != null) {
            val currentLocal = _locais.value.find { it.id == currentId } ?: break
            hierarchy.add(0, currentLocal) // Adiciona no início para manter a ordem correta
            currentId = currentLocal.parentId
        }

        // Valida se a hierarquia está ordenada por nível
        if (hierarchy.zipWithNext().any { (parent, child) -> child.level <= parent.level }) {
            Log.w("VendasViewModel", "Hierarquia com níveis inconsistentes detectada")
            return emptyList()
        }

        return hierarchy
    }

    fun addCliente(nome: String, telefone: String, localId: Long, sublocal1Id: Long?, sublocal2Id: Long?, sublocal3Id: Long?) {
        viewModelScope.launch {
            try {
                Log.d("VendasViewModel", """
                === Iniciando cadastro de novo cliente ===
                Nome: $nome
                Local ID: $localId
                Sublocal 1 ID: $sublocal1Id
                Sublocal 2 ID: $sublocal2Id
                Sublocal 3 ID: $sublocal3Id
            """.trimIndent())

                // Verifica a existência dos locais
                val local = localRepository.getLocalById(localId)
                    ?: throw IllegalStateException("Local principal não encontrado")

                // Validamos a hierarquia completa
                val hierarchy = listOfNotNull(
                    local,
                    sublocal1Id?.let { localRepository.getLocalById(it) },
                    sublocal2Id?.let { localRepository.getLocalById(it) },
                    sublocal3Id?.let { localRepository.getLocalById(it) }
                )

                // Validamos a ordem da hierarquia pelos níveis
                hierarchy.zipWithNext().forEach { (parent, child) ->
                    if (child.level <= parent.level) {
                        throw IllegalStateException("Hierarquia de locais inválida: nível do filho não é maior que do pai")
                    }
                }

                val novoCliente = Cliente(
                    nome = nome,
                    telefone = telefone,
                    localId = localId,
                    sublocal1Id = sublocal1Id,
                    sublocal2Id = sublocal2Id,
                    sublocal3Id = sublocal3Id
                )

                Log.d("VendasViewModel", """
                === Dados finais do cliente para cadastro ===
                Local principal: ${local.nome} (ID: ${local.id})
                Hierarquia completa: ${hierarchy.joinToString { it.nome }}
                Cliente: ${novoCliente.nome}
                - Local ID: ${novoCliente.localId}
                - Sublocal 1 ID: ${novoCliente.sublocal1Id}
                - Sublocal 2 ID: ${novoCliente.sublocal2Id}
                - Sublocal 3 ID: ${novoCliente.sublocal3Id}
            """.trimIndent())

                val clienteId = clienteRepository.insertCliente(novoCliente)
                Log.d("VendasViewModel", "Cliente cadastrado com sucesso! ID: $clienteId")

                // Reaplicar o filtro do local atualmente selecionado
                _localSelecionado.value?.let { localAtual ->
                    selecionarLocal(localAtual.id)
                }

            } catch (e: Exception) {
                Log.e("VendasViewModel", "Erro ao adicionar cliente", e)
                _error.value = "Erro ao adicionar cliente: ${e.message}"
            }
        }
    }

    private fun logHieraquiaClientes(localId: Long, clientes: List<Cliente>) {
        viewModelScope.launch {
            val local = localRepository.getLocalById(localId)
            Log.d("VendasViewModel", "=== Análise de Hierarquia de Clientes ===")
            Log.d("VendasViewModel", "Local selecionado: ${local?.nome} (ID: $localId)")
            Log.d("VendasViewModel", "Total de clientes nesta hierarquia: ${clientes.size}")

            // Agrupa clientes por nível na hierarquia
            // Agrupa clientes por nível na hierarquia
            val clientesPorNivel = clientes.groupBy { cliente ->
                when {
                    cliente.localId == localId -> "Principal"
                    cliente.sublocal1Id == localId -> "Sublocal 1"
                    cliente.sublocal2Id == localId -> "Sublocal 2"
                    cliente.sublocal3Id == localId -> "Sublocal 3"
                    else -> "Outro"
                }
            }

            clientesPorNivel.forEach { (nivel, clientesDoNivel) ->
                Log.d("VendasViewModel", "\n=== Clientes no nível: $nivel (${clientesDoNivel.size}) ===")
                clientesDoNivel.forEach { cliente ->
                    Log.d("VendasViewModel", """
                    Cliente: ${cliente.nome}
                    ID: ${cliente.id}
                    - Local principal: ${cliente.localId}
                    - Sublocal 1: ${cliente.sublocal1Id}
                    - Sublocal 2: ${cliente.sublocal2Id}
                    - Sublocal 3: ${cliente.sublocal3Id}
                    ---------------------
                """.trimIndent())
                }
            }
        }
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