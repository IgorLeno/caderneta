package com.example.caderneta.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.repository.VendaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * Balanço de caixa. Semântica dos números (valores em centavos):
 * - Total de vendas: soma das vendas (à vista + a prazo). Pagamentos de
 *   dívida NÃO são vendas e ficam de fora.
 * - Recebimentos: dinheiro que efetivamente entrou — vendas à vista +
 *   pagamentos de dívida.
 * Períodos: dia corrente, semana corrente (desde o primeiro dia da semana do
 * calendário local) e mês corrente (desde o dia 1º).
 */
class BalancoCaixaViewModel(
    private val vendaRepository: VendaRepository,
) : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
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
            val hoje =
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
            val inicioSemana =
                (hoje.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                }
            val inicioMes =
                (hoje.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                }
            val fimAberto = Date(Long.MAX_VALUE)

            combine(
                vendaRepository.getVendasByDateRange(hoje.time, fimAberto),
                vendaRepository.getVendasByDateRange(inicioSemana.time, fimAberto),
                vendaRepository.getVendasByDateRange(inicioMes.time, fimAberto),
            ) { diarias, semanais, mensais ->
                VendasState(
                    totalVendasDiariasCentavos = calcularTotalVendas(diarias),
                    totalRecebimentosDiariosCentavos = calcularTotalRecebimentos(diarias),
                    quantidadeOperacoesDiarias = diarias.size,
                    totalVendasSemanaisCentavos = calcularTotalVendas(semanais),
                    totalRecebimentosSemanaisCentavos = calcularTotalRecebimentos(semanais),
                    quantidadeOperacoesSemanais = semanais.size,
                    totalVendasMensaisCentavos = calcularTotalVendas(mensais),
                    totalRecebimentosMensaisCentavos = calcularTotalRecebimentos(mensais),
                    quantidadeOperacoesMensais = mensais.size,
                )
            }.onEach { _isLoading.value = false }
                .catch { e ->
                    _isLoading.value = false
                    _error.value = "Erro ao carregar dados: ${e.message}"
                }.collect { state -> _vendasState.value = state }
        }
    }

    private fun calcularTotalVendas(vendas: List<Venda>): Long =
        vendas.filter { it.transacao != TransacaoVenda.PAGAMENTO }.sumOf { it.valorCentavos }

    private fun calcularTotalRecebimentos(vendas: List<Venda>): Long =
        vendas
            .filter {
                it.transacao == TransacaoVenda.A_VISTA || it.transacao == TransacaoVenda.PAGAMENTO
            }.sumOf { it.valorCentavos }

    data class VendasState(
        val totalVendasDiariasCentavos: Long = 0,
        val totalRecebimentosDiariosCentavos: Long = 0,
        val quantidadeOperacoesDiarias: Int = 0,
        val totalVendasSemanaisCentavos: Long = 0,
        val totalRecebimentosSemanaisCentavos: Long = 0,
        val quantidadeOperacoesSemanais: Int = 0,
        val totalVendasMensaisCentavos: Long = 0,
        val totalRecebimentosMensaisCentavos: Long = 0,
        val quantidadeOperacoesMensais: Int = 0,
    )
}

class BalancoCaixaViewModelFactory(
    private val vendaRepository: VendaRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BalancoCaixaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BalancoCaixaViewModel(vendaRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
