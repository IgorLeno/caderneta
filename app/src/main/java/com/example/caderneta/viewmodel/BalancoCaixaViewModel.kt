package com.example.caderneta.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class BalancoCaixaViewModel(private val vendaRepository: VendaRepository) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _vendasState = MutableStateFlow(VendasState())
    val vendasState: StateFlow<VendasState> = _vendasState

    init {
        carregarBalanco()
    }

    private fun carregarBalanco() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val hoje = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                val inicioSemana = hoje.clone() as Calendar
                inicioSemana.add(Calendar.DAY_OF_WEEK, -7)
                val inicioMes = hoje.clone() as Calendar
                inicioMes.set(Calendar.DAY_OF_MONTH, 1)

                combine(
                    vendaRepository.getVendasByDateRange(hoje.time, Date()),
                    vendaRepository.getVendasByDateRange(inicioSemana.time, Date()),
                    vendaRepository.getVendasByDateRange(inicioMes.time, Date())
                ) { diarias, semanais, mensais ->
                    VendasState(
                        totalVendasDiarias = calcularTotalVendas(diarias),
                        totalRecebimentosDiarios = calcularTotalRecebimentos(diarias),
                        totalVendasSemanais = calcularTotalVendas(semanais),
                        totalRecebimentosSemanais = calcularTotalRecebimentos(semanais),
                        totalVendasMensais = calcularTotalVendas(mensais),
                        totalRecebimentosMensais = calcularTotalRecebimentos(mensais)
                    )
                }.catch { e ->
                    _error.value = "Erro ao carregar dados: ${e.message}"
                }.collect { state ->
                    _vendasState.value = state
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun calcularTotalVendas(vendas: List<Venda>): Double {
        return vendas.sumOf { it.valor }
    }

    private fun calcularTotalRecebimentos(vendas: List<Venda>): Double {
        return vendas.filter { it.transacao == "a_vista" }.sumOf { it.valor }
    }

    data class VendasState(
        val totalVendasDiarias: Double = 0.0,
        val totalRecebimentosDiarios: Double = 0.0,
        val totalVendasSemanais: Double = 0.0,
        val totalRecebimentosSemanais: Double = 0.0,
        val totalVendasMensais: Double = 0.0,
        val totalRecebimentosMensais: Double = 0.0
    )
}

class BalancoCaixaViewModelFactory(private val vendaRepository: VendaRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BalancoCaixaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BalancoCaixaViewModel(vendaRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}