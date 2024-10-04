package com.example.caderneta.ui.historico

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.R
import com.example.caderneta.databinding.FragmentHistoricoVendasBinding
import com.example.caderneta.viewmodel.HistoricoVendasViewModel
import com.example.caderneta.viewmodel.HistoricoVendasViewModelFactory
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class HistoricoVendasFragment : Fragment() {

    private var _binding: FragmentHistoricoVendasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: HistoricoVendasViewModel by viewModels {
        HistoricoVendasViewModelFactory(
            (requireActivity().application as CadernetaApplication).vendaRepository,
            (requireActivity().application as CadernetaApplication).clienteRepository,
            (requireActivity().application as CadernetaApplication).localRepository
        )
    }

    private lateinit var detalhesVendasAdapter: DetalhesVendasAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHistoricoVendasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabLayout()
        setupChipGroup()
        setupRecyclerView()
        setupChart()
        observeViewModel()
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.setPeriodoSelecionado(HistoricoVendasViewModel.Periodo.SEMANAL)
                    1 -> viewModel.setPeriodoSelecionado(HistoricoVendasViewModel.Periodo.MENSAL)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupChipGroup() {
        binding.chipGroupAgrupamento.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.chip_pessoa -> viewModel.setAgrupamentoSelecionado(HistoricoVendasViewModel.Agrupamento.PESSOA)
                R.id.chip_predio -> viewModel.setAgrupamentoSelecionado(HistoricoVendasViewModel.Agrupamento.PREDIO)
            }
        }
    }

    private fun setupRecyclerView() {
        detalhesVendasAdapter = DetalhesVendasAdapter()
        binding.rvDetalhesVendas.adapter = detalhesVendasAdapter
    }

    private fun setupChart() {
        binding.chartVendas.apply {
            description.isEnabled = false
            setDrawValueAboveBar(true)
            setDrawGridBackground(false)
            setPinchZoom(false)
            setScaleEnabled(false)
            legend.isEnabled = false

            xAxis.position = XAxis.XAxisPosition.BOTTOM
            xAxis.setDrawGridLines(false)
            xAxis.granularity = 1f

            axisLeft.setDrawGridLines(false)
            axisRight.isEnabled = false

            setFitBars(true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.progressBar.isVisible = isLoading
                binding.contentLayout.isVisible = !isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { errorMessage ->
                errorMessage?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.historicoVendas.collectLatest {
                atualizarGrafico()
                atualizarDetalhesVendas()
            }
        }
    }

    private fun atualizarGrafico() {
        val entries = when (viewModel.periodoSelecionado.value) {
            HistoricoVendasViewModel.Periodo.SEMANAL -> {
                viewModel.calcularTotalVendasPorSemana().map { (semana, total) ->
                    BarEntry(semana.toFloat(), total.toFloat())
                }
            }
            HistoricoVendasViewModel.Periodo.MENSAL -> {
                viewModel.calcularTotalVendasPorMes().map { (mes, total) ->
                    BarEntry(mes.toFloat(), total.toFloat())
                }
            }
        }

        val color = ContextCompat.getColor(requireContext(), R.color.primary_color)

        val dataSet = BarDataSet(entries, "Vendas").apply {
            setColors(color)
            valueFormatter = CurrencyValueFormatter()
        }

        val barData = BarData(dataSet).apply {
            barWidth = 0.9f
        }

        binding.chartVendas.apply {
            data = barData
            xAxis.valueFormatter = when (viewModel.periodoSelecionado.value) {
                HistoricoVendasViewModel.Periodo.SEMANAL -> WeekValueFormatter()
                HistoricoVendasViewModel.Periodo.MENSAL -> MonthValueFormatter()
            }
            invalidate()
        }
    }

    private fun atualizarDetalhesVendas() {
        val detalhes = when (viewModel.agrupamentoSelecionado.value) {
            HistoricoVendasViewModel.Agrupamento.PESSOA -> viewModel.agruparVendasPorPessoa()
            HistoricoVendasViewModel.Agrupamento.PREDIO -> viewModel.agruparVendasPorPredio()
        }

        val detalhesList = detalhes.map { (id, vendas) ->
            DetalheVenda(
                nome = when (viewModel.agrupamentoSelecionado.value) {
                    HistoricoVendasViewModel.Agrupamento.PESSOA -> viewModel.getClienteById(id)?.nome ?: "Cliente Desconhecido"
                    HistoricoVendasViewModel.Agrupamento.PREDIO -> viewModel.getLocalById(id)?.nome ?: "Local Desconhecido"
                },
                totalVendas = vendas.sumOf { it.total },
                quantidadeVendas = vendas.size
            )
        }

        detalhesVendasAdapter.submitList(detalhesList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class CurrencyValueFormatter : ValueFormatter() {
        private val format = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
        override fun getFormattedValue(value: Float): String = format.format(value)
    }

    inner class WeekValueFormatter : ValueFormatter() {
        override fun getFormattedValue(value: Float): String = "Semana ${value.toInt()}"
    }

    inner class MonthValueFormatter : ValueFormatter() {
        private val months = arrayOf("Jan", "Fev", "Mar", "Abr", "Mai", "Jun", "Jul", "Ago", "Set", "Out", "Nov", "Dez")
        override fun getFormattedValue(value: Float): String = months[value.toInt() - 1]
    }
}