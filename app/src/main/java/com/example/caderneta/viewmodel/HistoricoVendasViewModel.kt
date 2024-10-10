package com.example.caderneta.viewmodel

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class HistoricoVendasViewModel(
    private val vendaRepository: VendaRepository,
    private val clienteRepository: ClienteRepository,
    private val localRepository: LocalRepository
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
                val dataInicial = when (_periodoSelecionado.value) {
                    Periodo.SEMANAL -> Calendar.getInstance().apply {
                        add(Calendar.WEEK_OF_YEAR, -4) // Últimas 4 semanas
                        set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                    Periodo.MENSAL -> Calendar.getInstance().apply {
                        add(Calendar.MONTH, -6) // Últimos 6 meses
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.time
                }

                _historicoVendas.value = vendaRepository.getVendasByDateRange(dataInicial, dataAtual.time).first()
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

    fun agruparVendasPorPessoa(): Map<Long, List<Venda>> {
        return _historicoVendas.value.groupBy { it.clienteId }
    }

    fun agruparVendasPorPredio(): Map<Long, List<Venda>> {
        return _historicoVendas.value.groupBy { it.localId }
    }

    fun calcularTotalVendasPorSemana(): Map<Int, Double> {
        return _historicoVendas.value.groupBy { getWeekOfYear(it.data) }
            .mapValues { (_, vendas) -> vendas.sumOf { it.valor } }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun calcularTotalVendasPorMes(): Map<Int, Double> {
        return _historicoVendas.value.groupBy {
            LocalDate.ofInstant(it.data.toInstant(), ZoneId.systemDefault()).monthValue
        }.mapValues { (_, vendas) -> vendas.sumOf { it.valor } }
    }

    private fun getWeekOfYear(date: Date): Int {
        val cal = Calendar.getInstance()
        cal.time = date
        return cal.get(Calendar.WEEK_OF_YEAR)
    }

    fun getClienteById(id: Long): Cliente? = clientes[id]

    fun getLocalById(id: Long): Local? = locais[id]

    enum class Periodo {
        SEMANAL, MENSAL
    }

    enum class Agrupamento {
        PESSOA, PREDIO
    }
}

class HistoricoVendasViewModelFactory(
    private val vendaRepository: VendaRepository,
    private val clienteRepository: ClienteRepository,
    private val localRepository: LocalRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoricoVendasViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoricoVendasViewModel(vendaRepository, clienteRepository, localRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}