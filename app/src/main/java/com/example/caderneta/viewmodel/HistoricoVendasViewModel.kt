package com.example.caderneta.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * Histórico de VENDAS (pagamentos de dívida não entram aqui — são
 * recebimentos, não vendas). Valores em centavos.
 * Agrupamentos por período carregam o ano junto (semana 1 de 2027 nunca se
 * mistura com a semana 1 de 2026).
 */
class HistoricoVendasViewModel(
    private val vendaRepository: VendaRepository,
    private val clienteRepository: ClienteRepository,
    private val localRepository: LocalRepository,
) : ViewModel() {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _historicoVendas = MutableStateFlow<List<Venda>>(emptyList())
    val historicoVendas: StateFlow<List<Venda>> = _historicoVendas

    private val _periodoSelecionado = MutableStateFlow(Periodo.SEMANAL)
    val periodoSelecionado: StateFlow<Periodo> = _periodoSelecionado

    private val _agrupamentoSelecionado = MutableStateFlow(Agrupamento.PESSOA)
    val agrupamentoSelecionado: StateFlow<Agrupamento> = _agrupamentoSelecionado

    private var clientes: Map<Long, Cliente> = emptyMap()
    private var locais: Map<Long, Local> = emptyMap()

    init {
        carregarHistoricoVendas()
        carregarClientesELocais()
    }

    fun setPeriodoSelecionado(periodo: Periodo) {
        _periodoSelecionado.value = periodo
        carregarHistoricoVendas()
    }

    fun setAgrupamentoSelecionado(agrupamento: Agrupamento) {
        _agrupamentoSelecionado.value = agrupamento
    }

    private fun carregarHistoricoVendas() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val dataAtual = Calendar.getInstance()
                val dataInicial =
                    when (_periodoSelecionado.value) {
                        Periodo.SEMANAL ->
                            Calendar
                                .getInstance()
                                .apply {
                                    add(Calendar.WEEK_OF_YEAR, -4) // Últimas 4 semanas
                                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.time
                        Periodo.MENSAL ->
                            Calendar
                                .getInstance()
                                .apply {
                                    add(Calendar.MONTH, -6) // Últimos 6 meses
                                    set(Calendar.DAY_OF_MONTH, 1)
                                    set(Calendar.HOUR_OF_DAY, 0)
                                    set(Calendar.MINUTE, 0)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }.time
                    }

                _historicoVendas.value =
                    vendaRepository
                        .getVendasByDateRange(dataInicial, dataAtual.time)
                        .first()
                        .filter { it.transacao != TransacaoVenda.PAGAMENTO }
            } catch (e: Exception) {
                _error.value = "Erro ao carregar histórico de vendas: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun carregarClientesELocais() {
        viewModelScope.launch {
            try {
                clientes = clienteRepository.getAllClientes().first().associateBy { it.id }
                locais = localRepository.getAllLocais().first().associateBy { it.id }
            } catch (e: Exception) {
                _error.value = "Erro ao carregar clientes e locais: ${e.message}"
            }
        }
    }

    fun agruparVendasPorPessoa(): Map<Long, List<Venda>> = _historicoVendas.value.groupBy { it.clienteId }

    fun agruparVendasPorPredio(): Map<Long, List<Venda>> = _historicoVendas.value.groupBy { it.localId ?: -1L }

    /** Totais por semana, em ordem cronológica: rótulo "S<semana>" -> centavos. */
    fun calcularTotalVendasPorSemana(): List<Pair<String, Long>> =
        _historicoVendas.value
            .groupBy { anoSemana(it.data) }
            .toSortedMap()
            .map { (chave, vendas) -> "S${chave % 100}" to vendas.sumOf { it.valorCentavos } }

    /** Totais por mês, em ordem cronológica: rótulo "Jan".."Dez" -> centavos. */
    fun calcularTotalVendasPorMes(): List<Pair<String, Long>> =
        _historicoVendas.value
            .groupBy { anoMes(it.data) }
            .toSortedMap()
            .map { (chave, vendas) -> NOMES_MESES[(chave % 100) - 1] to vendas.sumOf { it.valorCentavos } }

    /** Chave ordenável ano*100+semana (ex.: 202627). */
    private fun anoSemana(date: Date): Int {
        val cal = Calendar.getInstance().apply { time = date }
        // Na virada do ano a semana 1 pode começar em dezembro: usa o ano da semana.
        val semana = cal.get(Calendar.WEEK_OF_YEAR)
        var ano = cal.get(Calendar.YEAR)
        if (semana == 1 && cal.get(Calendar.MONTH) == Calendar.DECEMBER) ano += 1
        if (semana >= 52 && cal.get(Calendar.MONTH) == Calendar.JANUARY) ano -= 1
        return ano * 100 + semana
    }

    /** Chave ordenável ano*100+mês (ex.: 202607). */
    private fun anoMes(date: Date): Int {
        val cal = Calendar.getInstance().apply { time = date }
        return cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH) + 1
    }

    fun getClienteById(id: Long): Cliente? = clientes[id]

    fun getLocalById(id: Long): Local? = locais[id]

    enum class Periodo {
        SEMANAL,
        MENSAL,
    }

    enum class Agrupamento {
        PESSOA,
        PREDIO,
    }

    companion object {
        private val NOMES_MESES =
            arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
    }
}

class HistoricoVendasViewModelFactory(
    private val vendaRepository: VendaRepository,
    private val clienteRepository: ClienteRepository,
    private val localRepository: LocalRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoricoVendasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoricoVendasViewModel(vendaRepository, clienteRepository, localRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
