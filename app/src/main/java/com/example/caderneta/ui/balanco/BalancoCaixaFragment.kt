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
import com.example.caderneta.viewmodel.BalancoCaixaViewModel
import com.example.caderneta.viewmodel.BalancoCaixaViewModelFactory
import com.example.caderneta.data.entity.Venda
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class BalancoCaixaFragment : Fragment() {

    private var _binding: FragmentBalancoCaixaBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BalancoCaixaViewModel by viewModels {
        BalancoCaixaViewModelFactory((requireActivity().application as CadernetaApplication).vendaRepository)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBalancoCaixaBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
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
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
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
            cardVendasDiarias.apply {
                setTotalVendas("Total: ${formatCurrency(state.totalVendasDiarias)}")
                setTotalRecebimentos("Recebimentos: ${formatCurrency(state.totalRecebimentosDiarios)}")
            }
            cardVendasSemanais.apply {
                setTotalVendas("Total: ${formatCurrency(state.totalVendasSemanais)}")
                setTotalRecebimentos("Recebimentos: ${formatCurrency(state.totalRecebimentosSemanais)}")
            }
            cardVendasMensais.apply {
                setTotalVendas("Total: ${formatCurrency(state.totalVendasMensais)}")
                setTotalRecebimentos("Recebimentos: ${formatCurrency(state.totalRecebimentosMensais)}")
            }
        }
    }

    private fun formatCurrency(value: Double): String {
        return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}