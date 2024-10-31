package com.example.caderneta.ui.consultas

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.R
import com.example.caderneta.data.entity.Local
import com.example.caderneta.databinding.FragmentConsultasBinding
import com.example.caderneta.ui.vendas.LocalAdapter
import com.example.caderneta.viewmodel.ConsultasViewModel
import com.example.caderneta.viewmodel.ConsultasViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConsultasFragment : Fragment() {

    private var _binding: FragmentConsultasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConsultasViewModel by viewModels {
        ConsultasViewModelFactory(
            (requireActivity().application as CadernetaApplication).clienteRepository,
            (requireActivity().application as CadernetaApplication).vendaRepository,
            (requireActivity().application as CadernetaApplication).localRepository,
            (requireActivity().application as CadernetaApplication).contaRepository
        )
    }

    private lateinit var resultadosAdapter: ResultadosConsultaAdapter
    private lateinit var localAdapter: LocalConsultaAdapter
    private var searchDebounceJob: kotlinx.coroutines.Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConsultasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        setupToolbar()
        setupRecyclerView()
        setupNavDrawer()
        setupLocalRecyclerView()
        setupSearchListeners()
    }

    private fun setupToolbar() {
        binding.tvLocalSelecionado.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.level_0)
        )
    }

    private fun setupRecyclerView() {
        resultadosAdapter = ResultadosConsultaAdapter(
            onLocalClick = { localId ->
                viewModel.selecionarLocal(localId)
            },
            onClienteClick = { clienteId ->
                viewModel.carregarVendasPorCliente(clienteId)
            },
            onLimparExtratoClick = { clienteId ->
                viewModel.limparExtrato(clienteId)
            },
            localRepository = (requireActivity().application as CadernetaApplication).localRepository
        )

        binding.rvResultados.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = resultadosAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupNavDrawer() {
        val toggle = ActionBarDrawerToggle(
            requireActivity(),
            binding.drawerLayout,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        ).apply {
            drawerArrowDrawable.color = ContextCompat.getColor(requireContext(), R.color.on_primary)
        }

        binding.drawerLayout.apply {
            addDrawerListener(toggle)
            addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
                override fun onDrawerOpened(drawerView: View) {
                    Log.d("ConsultasFragment", "Menu lateral aberto")
                }
                override fun onDrawerClosed(drawerView: View) {
                    binding.navView.findViewById<TextInputEditText>(R.id.et_pesquisar_local).text?.clear()
                    Log.d("ConsultasFragment", "Menu lateral fechado")
                }
                override fun onDrawerStateChanged(newState: Int) {}
            })
        }
        toggle.syncState()
    }


    private fun setupLocalRecyclerView() {
        // Substituir o LocalAdapter pelo LocalConsultaAdapter
        localAdapter = LocalConsultaAdapter(
            onLocalClick = { local: Local ->
                viewModel.selecionarLocal(local.id)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onToggleExpand = { local: Local ->
                viewModel.toggleLocalExpansion(local)
            }
        )

        binding.navView.findViewById<RecyclerView>(R.id.rv_locais).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = localAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupSearchListeners() {
        // Busca de clientes
        binding.etBusca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300) // Debounce de 300ms
                    val query = s.toString().trim()
                    Log.d("ConsultasFragment", "Buscando clientes: '$query'")
                    viewModel.buscarClientes(query)
                }
            }
        })

        // Busca de locais no menu lateral
        binding.navView.findViewById<TextInputEditText>(R.id.et_pesquisar_local)
            .addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val query = s.toString().trim()
                    Log.d("ConsultasFragment", "Buscando locais: '$query'")
                    viewModel.buscarLocais(query)
                }
            })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locais.collectLatest { locais ->
                Log.d("ConsultasFragment", "Atualizando lista de locais: ${locais.size} locais")
                localAdapter.updateLocais(locais)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.localSelecionado.collectLatest { local ->
                binding.tvLocalSelecionado.text = local?.nome ?: "Todos os locais"
                Log.d("ConsultasFragment", "Local selecionado atualizado: ${local?.nome}")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.clientesComSaldo.collectLatest { clientesComSaldo ->
                Log.d(
                    "ConsultasFragment",
                    "Atualizando lista de clientes: ${clientesComSaldo.size} clientes"
                )
                resultadosAdapter.submitList(clientesComSaldo.map { (cliente, saldo) ->
                    ResultadoConsulta.Cliente(cliente, saldo)
                })
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.vendasPorCliente.collectLatest { vendasPorCliente ->
                Log.d(
                    "ConsultasFragment",
                    "Atualizando vendas por cliente: ${vendasPorCliente.size} clientes com vendas"
                )
                resultadosAdapter.updateVendasPorCliente(vendasPorCliente)
            }
        }

        // Observar erros
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { errorMessage ->
                errorMessage?.let { message ->
                    Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchDebounceJob?.cancel()
        _binding = null
    }
}