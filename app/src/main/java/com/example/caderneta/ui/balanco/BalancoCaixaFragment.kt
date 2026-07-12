package com.example.caderneta.ui.balanco

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.databinding.FragmentBalancoCaixaBinding
import com.example.caderneta.ui.common.FeedbackPresenter
import com.example.caderneta.ui.components.CardBalancoView
import com.example.caderneta.util.centavosParaReais
import com.example.caderneta.viewmodel.BalancoCaixaViewModel
import com.example.caderneta.viewmodel.BalancoCaixaViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class BalancoCaixaFragment : Fragment() {
    private var _binding: FragmentBalancoCaixaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BalancoCaixaViewModel by viewModels {
        BalancoCaixaViewModelFactory((requireActivity().application as CadernetaApplication).vendaRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentBalancoCaixaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupCardViews()
        observeViewModel()
    }

    private fun setupCardViews() {
        binding.cardVendasDiarias.setTitulo("Vendas Diárias")
        binding.cardVendasSemanais.setTitulo("Vendas Semanais")
        binding.cardVendasMensais.setTitulo("Vendas Mensais")
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
                    FeedbackPresenter.erro(binding.root, it)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.vendasState.collectLatest { state ->
                updateVendasCards(state)
            }
        }
    }

    private fun updateVendasCards(state: BalancoCaixaViewModel.VendasState) {
        with(binding) {
            cardVendasDiarias.atualizar(
                vendasCentavos = state.totalVendasDiariasCentavos,
                recebimentosCentavos = state.totalRecebimentosDiariosCentavos,
                quantidadeOperacoes = state.quantidadeOperacoesDiarias,
            )
            cardVendasSemanais.atualizar(
                vendasCentavos = state.totalVendasSemanaisCentavos,
                recebimentosCentavos = state.totalRecebimentosSemanaisCentavos,
                quantidadeOperacoes = state.quantidadeOperacoesSemanais,
            )
            cardVendasMensais.atualizar(
                vendasCentavos = state.totalVendasMensaisCentavos,
                recebimentosCentavos = state.totalRecebimentosMensaisCentavos,
                quantidadeOperacoes = state.quantidadeOperacoesMensais,
            )
        }
    }

    private fun CardBalancoView.atualizar(
        vendasCentavos: Long,
        recebimentosCentavos: Long,
        quantidadeOperacoes: Int,
    ) {
        setVazio(vendasCentavos == 0L && recebimentosCentavos == 0L && quantidadeOperacoes == 0)
        setTotalVendas("Vendas: ${vendasCentavos.centavosParaReais()}")
        setTotalRecebimentos("Recebimentos: ${recebimentosCentavos.centavosParaReais()}")
        setQuantidadeOperacoes("Operações: $quantidadeOperacoes")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
