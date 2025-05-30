package com.example.caderneta.ui.consultas

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.R
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.FragmentConsultasBinding
import com.example.caderneta.ui.vendas.NovoClienteDialog
import com.example.caderneta.viewmodel.ConsultasViewModel
import com.example.caderneta.viewmodel.ConsultasViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.example.caderneta.viewmodel.VendasViewModel
import com.example.caderneta.viewmodel.VendasViewModelFactory

class ConsultasFragment : Fragment() {

    private var _binding: FragmentConsultasBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConsultasViewModel by viewModels {
        ConsultasViewModelFactory(
            (requireActivity().application as CadernetaApplication).clienteRepository,
            (requireActivity().application as CadernetaApplication).vendaRepository,
            (requireActivity().application as CadernetaApplication).localRepository,
            (requireActivity().application as CadernetaApplication).contaRepository,
            (requireActivity().application as CadernetaApplication).operacaoRepository,
            (requireActivity().application as CadernetaApplication).configuracoesRepository
        )
    }

    private val vendasViewModel: VendasViewModel by viewModels {
        VendasViewModelFactory(
            (requireActivity().application as CadernetaApplication).clienteRepository,
            (requireActivity().application as CadernetaApplication).localRepository,
            (requireActivity().application as CadernetaApplication).produtoRepository,
            (requireActivity().application as CadernetaApplication).vendaRepository,
            (requireActivity().application as CadernetaApplication).itemVendaRepository,
            (requireActivity().application as CadernetaApplication).configuracoesRepository,
            (requireActivity().application as CadernetaApplication).operacaoRepository,
            (requireActivity().application as CadernetaApplication).contaRepository
        )
    }

    private lateinit var resultadosAdapter: ResultadosConsultaAdapter
    private lateinit var localAdapter: LocalConsultaAdapter
    private var searchDebounceJob: Job? = null

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

        // Forçar carregamento inicial dos locais
        viewLifecycleOwner.lifecycleScope.launch {
            // Pequeno delay para garantir que o ViewModel esteja pronto
            delay(100)
            viewModel.carregarDados()
        }

        setupUI()
        setupBackNavigation()
        observeViewModel()
        handleNavigationArgs()

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.saldosAtualizados.collectLatest { saldos ->
                Log.d("SaldoDebug", "Recebendo saldos atualizados: $saldos")
                if (saldos.isNotEmpty()) {
                    val currentList = resultadosAdapter.currentList.toMutableList()
                    var hasChanges = false

                    saldos.forEach { (clienteId, novoSaldo) ->
                        Log.d(
                            "SaldoDebug",
                            "Processando cliente $clienteId - Novo saldo: $novoSaldo"
                        )
                        val index = currentList.indexOfFirst {
                            (it is ResultadoConsulta.Cliente && it.cliente.id == clienteId)
                        }

                        if (index != -1) {
                            val item = currentList[index]
                            if (item is ResultadoConsulta.Cliente) {
                                Log.d(
                                    "SaldoDebug",
                                    "Cliente encontrado na posição $index - Saldo anterior: ${item.saldo}"
                                )
                                currentList[index] = ResultadoConsulta.Cliente(
                                    item.cliente,
                                    novoSaldo
                                )
                                hasChanges = true
                            }
                        }
                    }

                    if (hasChanges) {
                        Log.d("SaldoDebug", "Submitting nova lista com saldos atualizados")
                        resultadosAdapter.submitList(null)
                        resultadosAdapter.submitList(currentList)
                    }
                }
            }
        }
    }



    private fun handleNavigationArgs() {
        val args = ConsultasFragmentArgs.fromBundle(requireArguments())
        if (args.clienteId != -1L && args.localId != -1L) {
            lifecycleScope.launch {
                try {
                    viewModel.selecionarLocal(args.localId)
                    delay(100)
                    args.filtroNomeCliente?.let { nome ->
                        binding.etBusca.setText(nome)
                        viewModel.buscarClientes(nome)
                    }
                    viewModel.carregarVendasPorCliente(args.clienteId)
                } catch (e: Exception) {
                    Log.e("ConsultasFragment", "Erro ao processar argumentos", e)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupBackNavigation()
    }

    private fun setupBackNavigation() {
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    findNavController().navigateUp()
                }
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callback)
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                // Tenta navegar de volta quando o botão da toolbar é clicado
                try {
                    findNavController().navigateUp()
                    true
                } catch (e: Exception) {
                    Log.e("ConsultasFragment", "Erro na navegação pela toolbar", e)
                    findNavController().navigate(R.id.vendasFragment)
                    true
                }
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupUI() {
        setupToolbar()
        setupRecyclerView()
        setupNavDrawer()
        setupLocalRecyclerView()
        setupSearchListeners()

        // Inicializar estado do drawer
        initializeDrawerState()
    }

    private fun setupToolbar() {
        binding.tvLocalSelecionado.apply {
            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.level_0))
            text = getString(R.string.todos_locais)
        }
    }

    private fun setupRecyclerView() {
// Dentro da setupRecyclerView()
        resultadosAdapter = ResultadosConsultaAdapter(
            onLocalClick = { localId ->
                viewModel.selecionarLocal(localId)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onClienteClick = { clienteId ->
                viewModel.carregarVendasPorCliente(clienteId)
            },
            onExtratoItemClick = { venda, cliente ->
                showOpcoesExtratoDialog(venda, cliente)
            },
            onEditarCliente = { cliente ->
                showEditClienteDialog(cliente)
            },
            onExcluirCliente = { cliente ->
                vendasViewModel.excluirCliente(cliente)
            },
            localRepository = (requireActivity().application as CadernetaApplication).localRepository,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            fragmentManager = childFragmentManager
        )

        binding.rvResultados.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultadosAdapter
        }
    }

    private fun showEditClienteDialog(cliente: Cliente) {
        vendasViewModel.reloadLocais()
        val dialog = NovoClienteDialog(vendasViewModel).apply {
            setClienteExistente(cliente)
            onClienteAdicionado = { nome, telefone, localId, sublocal1Id, sublocal2Id, sublocal3Id ->
                vendasViewModel.editarCliente(
                    cliente = cliente,
                    novoNome = nome,
                    novoTelefone = telefone,
                    novoLocalId = localId,
                    novoSublocal1Id = sublocal1Id,
                    novoSublocal2Id = sublocal2Id,
                    novoSublocal3Id = sublocal3Id
                )
            }
        }
        dialog.show(childFragmentManager, "EditClienteDialog")
    }

    private fun showOpcoesExtratoDialog(venda: Venda, cliente: Cliente) {
        OpcoesExtratoDialog(
            venda = venda,
            cliente = cliente,
            onEditarData = { vendaAtual, novaData ->
                viewModel.atualizarDataVenda(vendaAtual, novaData)
            },
            onEditarOperacao = { vendaAtual ->
                showEditarOperacaoDialog(vendaAtual, cliente)
            },
            onExcluir = { vendaAtual ->
                viewModel.excluirVenda(vendaAtual)
            }
        ).show(childFragmentManager, OpcoesExtratoDialog.TAG)
    }

    private fun showEditarOperacaoDialog(venda: Venda, cliente: Cliente) {
        EditarOperacaoDialog(
            venda = venda,
            cliente = cliente
        ).show(childFragmentManager, EditarOperacaoDialog.TAG)
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
            addDrawerListener(createDrawerListener())
        }
        toggle.syncState()
    }

    private fun createDrawerListener() = object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
        override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}

        override fun onDrawerOpened(drawerView: View) {
            Log.d("ConsultasFragment", "Menu lateral aberto")
        }

        override fun onDrawerClosed(drawerView: View) {
            binding.navView.findViewById<TextInputEditText>(R.id.et_pesquisar_local).text?.clear()
            Log.d("ConsultasFragment", "Menu lateral fechado")
        }

        override fun onDrawerStateChanged(newState: Int) {}
    }

    private fun setupLocalRecyclerView() {
        localAdapter = LocalConsultaAdapter(
            onLocalClick = { local ->
                viewModel.selecionarLocal(local.id)
                binding.etBusca.text?.clear()  // Clear search field
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onToggleExpand = { local ->
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
        setupClienteSearch()
        setupLocalSearch()
    }

    private fun setupClienteSearch() {
        binding.etBusca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    kotlinx.coroutines.delay(300)
                    val query = s.toString().trim()
                    Log.d("ConsultasFragment", "Buscando clientes: '$query'")
                    viewModel.buscarClientes(query)
                }
            }
        })
    }

    private fun setupLocalSearch() {
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

    private fun initializeDrawerState() {
        binding.navView.findViewById<TextInputEditText>(R.id.et_pesquisar_local).text?.clear()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.apply {
            launch {
                viewModel.locais.collectLatest { locais ->
                    Log.d("ConsultasFragment", "Atualizando lista de locais: ${locais.size} locais")
                    localAdapter.updateLocais(locais)
                }
            }

            launch {
                viewModel.localSelecionado.collectLatest { local ->
                    binding.tvLocalSelecionado.text = local?.nome ?: getString(R.string.todos_locais)
                    Log.d("ConsultasFragment", "Local selecionado atualizado: ${local?.nome}")
                }
            }

            launch {
                viewModel.clientesComSaldo.collectLatest { clientesComSaldo ->
                    Log.d("SaldoDebug", "Nova lista de clientes recebida: ${
                        clientesComSaldo.map { (cliente, saldo) ->
                            "${cliente.id}: $saldo"
                        }
                    }")
                    resultadosAdapter.submitList(clientesComSaldo.map { (cliente, saldo) ->
                        ResultadoConsulta.Cliente(cliente, saldo)
                    })
                }
            }

            launch {
                viewModel.vendasPorCliente.collectLatest { vendasPorCliente ->
                    Log.d("ConsultasFragment",
                        "Atualizando vendas por cliente: ${vendasPorCliente.size} clientes com vendas")
                    resultadosAdapter.updateVendasPorCliente(vendasPorCliente)
                }
            }

            launch {
                viewModel.error.collectLatest { errorMessage ->
                    errorMessage?.let { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }

            launch {
                viewModel.saldoAtualizado.collectLatest { clienteId ->
                    // Força uma atualização imediata do adapter
                    val currentList = resultadosAdapter.currentList.toMutableList()
                    val index = currentList.indexOfFirst {
                        (it is ResultadoConsulta.Cliente && it.cliente.id == clienteId)
                    }

                    if (index != -1) {
                        val item = currentList[index]
                        if (item is ResultadoConsulta.Cliente) {
                            val novoSaldo = viewModel.getSaldoCliente(clienteId)
                            Log.d(TAG, "Atualizando UI para cliente $clienteId - Novo saldo: $novoSaldo")

                            // Criar novo item com saldo atualizado
                            currentList[index] = ResultadoConsulta.Cliente(item.cliente, novoSaldo)

                            // Força atualização da RecyclerView
                            resultadosAdapter.submitList(null)
                            resultadosAdapter.submitList(currentList)
                        }
                    }

                    // Recarrega as vendas
                    viewModel.carregarVendasPorCliente(clienteId)
                }
            }

            // Observar erros
            launch {
                viewModel.error.collectLatest { errorMessage ->
                    errorMessage?.let { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                        viewModel.clearError()
                    }
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        searchDebounceJob?.cancel()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Limpar quaisquer recursos persistentes
        viewModel.clearSearch()
    }

    companion object {
        private const val TAG = "ConsultasFragment"
    }
}