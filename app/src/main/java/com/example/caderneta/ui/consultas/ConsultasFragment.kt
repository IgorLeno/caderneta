package com.example.caderneta.ui.consultas

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.databinding.FragmentConsultasBinding
import com.example.caderneta.viewmodel.ConsultasViewModel
import com.example.caderneta.viewmodel.ConsultasViewModelFactory
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class ConsultasFragment : Fragment() {

    private var _binding: FragmentConsultasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConsultasViewModel by viewModels {
        ConsultasViewModelFactory(
            (requireActivity().application as CadernetaApplication).clienteRepository,
            (requireActivity().application as CadernetaApplication).vendaRepository,
            (requireActivity().application as CadernetaApplication).localRepository
        )
    }

    private lateinit var resultadosAdapter: ResultadosConsultaAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConsultasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabLayout()
        setupRecyclerView()
        setupSearch()
        observeViewModel()
    }

    private fun setupTabLayout() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> viewModel.setModoBusca(ConsultasViewModel.ModoBusca.POR_PREDIO)
                    1 -> viewModel.setModoBusca(ConsultasViewModel.ModoBusca.POR_NOME)
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        resultadosAdapter = ResultadosConsultaAdapter(
            onLocalClick = { localId -> viewModel.carregarClientesPorLocal(localId) },
            onClienteClick = { clienteId -> viewModel.carregarVendasPorCliente(clienteId) },
            onLimparExtratoClick = { clienteId -> viewModel.limparExtrato(clienteId) }
        )
        binding.rvResultados.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultadosAdapter
        }
    }

    private fun setupSearch() {
        binding.etBusca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                viewModel.buscar(query)
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.modoBusca.collect { modo ->
                binding.tilBusca.hint = when (modo) {
                    ConsultasViewModel.ModoBusca.POR_PREDIO -> "Buscar por prédio"
                    ConsultasViewModel.ModoBusca.POR_NOME -> "Buscar por nome"
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locais.collect { locais ->
                resultadosAdapter.submitList(locais.map { ResultadoConsulta.Local(it) })
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.clientes.collect { clientes ->
                resultadosAdapter.submitList(clientes.map { ResultadoConsulta.Cliente(it) })
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.vendasPorCliente.collect { vendasPorCliente ->
                resultadosAdapter.updateVendasPorCliente(vendasPorCliente)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}