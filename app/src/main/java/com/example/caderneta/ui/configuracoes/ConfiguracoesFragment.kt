package com.example.caderneta.ui.configuracoes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.databinding.FragmentConfiguracoesBinding
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.viewmodel.ConfiguracoesViewModel
import com.example.caderneta.viewmodel.ConfiguracoesViewModelFactory
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConfiguracoesFragment : Fragment() {

    private var _binding: FragmentConfiguracoesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConfiguracoesViewModel by viewModels {
        ConfiguracoesViewModelFactory(
            ConfiguracoesRepository(
                (requireActivity().application as CadernetaApplication).database.configuracoesDao()
            )
        )
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConfiguracoesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.configuracoes.collectLatest { configuracoes ->
                configuracoes?.let { updateUI(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.promocoesAtivadas.collectLatest { ativadas ->
                binding.tlPromocoes.visibility = if (ativadas) View.VISIBLE else View.GONE
                binding.tvPromocoesStatus.text = if (ativadas) "Promoções ativadas" else "Promoções desativadas"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { errorMsg ->
                errorMsg?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun updateUI(configuracoes: Configuracoes) {
        binding.apply {
            etSalgadoVista.setText(configuracoes.precoSalgadoVista.toString())
            etSalgadoPrazo.setText(configuracoes.precoSalgadoPrazo.toString())
            etSucoVista.setText(configuracoes.precoSucoVista.toString())
            etSucoPrazo.setText(configuracoes.precoSucoPrazo.toString())
            switchPromocoes.isChecked = configuracoes.promocoesAtivadas
            etPromo1Nome.setText(configuracoes.promo1Nome)
            etPromo1Salgados.setText(configuracoes.promo1Salgados.toString())
            etPromo1Sucos.setText(configuracoes.promo1Sucos.toString())
            etPromo1Vista.setText(configuracoes.promo1Vista.toString())
            etPromo1Prazo.setText(configuracoes.promo1Prazo.toString())
            etPromo2Nome.setText(configuracoes.promo2Nome)
            etPromo2Salgados.setText(configuracoes.promo2Salgados.toString())
            etPromo2Sucos.setText(configuracoes.promo2Sucos.toString())
            etPromo2Vista.setText(configuracoes.promo2Vista.toString())
            etPromo2Prazo.setText(configuracoes.promo2Prazo.toString())
        }
    }

    private fun setupListeners() {
        binding.switchPromocoes.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setPromocoesAtivadas(isChecked)
        }

        binding.btnSalvarConfiguracoes.setOnClickListener {
            val novasConfiguracoes = Configuracoes(
                precoSalgadoVista = binding.etSalgadoVista.text.toString().toDoubleOrNull() ?: 0.0,
                precoSalgadoPrazo = binding.etSalgadoPrazo.text.toString().toDoubleOrNull() ?: 0.0,
                precoSucoVista = binding.etSucoVista.text.toString().toDoubleOrNull() ?: 0.0,
                precoSucoPrazo = binding.etSucoPrazo.text.toString().toDoubleOrNull() ?: 0.0,
                promocoesAtivadas = binding.switchPromocoes.isChecked,
                promo1Nome = binding.etPromo1Nome.text.toString(),
                promo1Salgados = binding.etPromo1Salgados.text.toString().toIntOrNull() ?: 0,
                promo1Sucos = binding.etPromo1Sucos.text.toString().toIntOrNull() ?: 0,
                promo1Vista = binding.etPromo1Vista.text.toString().toDoubleOrNull() ?: 0.0,
                promo1Prazo = binding.etPromo1Prazo.text.toString().toDoubleOrNull() ?: 0.0,
                promo2Nome = binding.etPromo2Nome.text.toString(),
                promo2Salgados = binding.etPromo2Salgados.text.toString().toIntOrNull() ?: 0,
                promo2Sucos = binding.etPromo2Sucos.text.toString().toIntOrNull() ?: 0,
                promo2Vista = binding.etPromo2Vista.text.toString().toDoubleOrNull() ?: 0.0,
                promo2Prazo = binding.etPromo2Prazo.text.toString().toDoubleOrNull() ?: 0.0
            )
            viewModel.salvarConfiguracoes(novasConfiguracoes)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}