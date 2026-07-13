package com.example.caderneta.ui.consultas

import android.net.Uri
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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.R
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.FragmentConsultasBinding
import com.example.caderneta.ui.common.FeedbackPresenter
import com.example.caderneta.ui.vendas.NovoClienteDialog
import com.example.caderneta.util.rethrowCancellation
import com.example.caderneta.viewmodel.ConsultasViewModel
import com.example.caderneta.viewmodel.ConsultasViewModelFactory
import com.example.caderneta.viewmodel.UiEvento
import com.example.caderneta.viewmodel.VendasViewModel
import com.example.caderneta.viewmodel.VendasViewModelFactory
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
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
            (requireActivity().application as CadernetaApplication).contaRepository,
            (requireActivity().application as CadernetaApplication).configuracoesRepository,
            (requireActivity().application as CadernetaApplication).financeiroService,
        )
    }

    private val vendasViewModel: VendasViewModel by viewModels {
        VendasViewModelFactory(
            (requireActivity().application as CadernetaApplication).clienteRepository,
            (requireActivity().application as CadernetaApplication).localRepository,
            (requireActivity().application as CadernetaApplication).vendaRepository,
            (requireActivity().application as CadernetaApplication).configuracoesRepository,
            (requireActivity().application as CadernetaApplication).contaRepository,
            (requireActivity().application as CadernetaApplication).financeiroService,
            (requireActivity().application as CadernetaApplication).clientePhotoRepository,
            (requireActivity().application as CadernetaApplication).clientePhotoSource,
        )
    }

    private lateinit var resultadosAdapter: ResultadosConsultaAdapter
    private lateinit var localAdapter: LocalConsultaAdapter
    private var searchDebounceJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentConsultasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupUI()
        setupBackNavigation()
        setupDialogResultListeners()
        observeViewModel()
        handleNavigationArgs()
    }

    private fun handleNavigationArgs() {
        val args = ConsultasFragmentArgs.fromBundle(requireArguments())
        if (args.clienteId != -1L && args.localId != -1L) {
            lifecycleScope.launch {
                try {
                    viewModel.selecionarLocalAguardando(args.localId)
                    args.filtroNomeCliente?.let { nome ->
                        binding.etBusca.setText(nome)
                        viewModel.buscarClientes(nome)
                    }
                    viewModel.carregarVendasPorCliente(args.clienteId)
                } catch (e: Exception) {
                    e.rethrowCancellation()
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
        val callback =
            object : OnBackPressedCallback(true) {
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                // Tenta navegar de volta quando o botão da toolbar é clicado
                try {
                    findNavController().navigateUp()
                    true
                } catch (e: Exception) {
                    e.rethrowCancellation()
                    Log.e("ConsultasFragment", "Erro na navegação pela toolbar", e)
                    findNavController().navigate(R.id.vendasFragment)
                    true
                }
            }
            else -> super.onOptionsItemSelected(item)
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
        resultadosAdapter =
            ResultadosConsultaAdapter(
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
                onClienteCollapse = { clienteId ->
                    viewModel.fecharVendasPorCliente(clienteId)
                },
                getClientePhotoFile = { fotoNome ->
                    vendasViewModel.arquivoFotoCliente(fotoNome)
                },
                localRepository = (requireActivity().application as CadernetaApplication).localRepository,
                coroutineScope = viewLifecycleOwner.lifecycleScope,
                fragmentManager = childFragmentManager,
            )

        binding.rvResultados.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = resultadosAdapter
        }
    }

    private fun setupDialogResultListeners() {
        childFragmentManager.setFragmentResultListener(
            OpcoesConsultaClienteDialog.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val clienteId = bundle.getLong(OpcoesConsultaClienteDialog.RESULT_CLIENTE_ID)
            viewLifecycleOwner.lifecycleScope.launch {
                val cliente =
                    (requireActivity().application as CadernetaApplication)
                        .clienteRepository
                        .getClienteById(clienteId)
                        ?: return@launch
                when (bundle.getString(OpcoesConsultaClienteDialog.RESULT_ACTION)) {
                    OpcoesConsultaClienteDialog.ACTION_SELL -> navigateToVendasForCliente(cliente)
                    OpcoesConsultaClienteDialog.ACTION_EDIT -> showEditClienteDialog(cliente)
                    OpcoesConsultaClienteDialog.ACTION_DELETE -> vendasViewModel.excluirCliente(cliente)
                }
            }
        }
        childFragmentManager.setFragmentResultListener(
            NovoClienteDialog.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle -> handleClienteDialogResult(bundle) }
        childFragmentManager.setFragmentResultListener(
            OpcoesExtratoDialog.REQUEST_KEY,
            viewLifecycleOwner,
        ) { _, bundle -> handleOpcoesExtratoResult(bundle) }
    }

    private suspend fun navigateToVendasForCliente(cliente: Cliente) {
        try {
            val localRepository = (requireActivity().application as CadernetaApplication).localRepository
            val localMaisEspecifico =
                listOfNotNull(
                    localRepository.getLocalById(cliente.localId),
                    cliente.sublocal1Id?.let { localRepository.getLocalById(it) },
                    cliente.sublocal2Id?.let { localRepository.getLocalById(it) },
                    cliente.sublocal3Id?.let { localRepository.getLocalById(it) },
                ).lastOrNull()

            val navController = findNavController()
            val destinationListener =
                object : NavController.OnDestinationChangedListener {
                    override fun onDestinationChanged(
                        controller: NavController,
                        destination: NavDestination,
                        arguments: Bundle?,
                    ) {
                        if (destination.id == R.id.vendasFragment) {
                            controller.removeOnDestinationChangedListener(this)
                            localMaisEspecifico?.let { local -> viewModel.selecionarLocal(local.id) }
                        }
                    }
                }
            navController.addOnDestinationChangedListener(destinationListener)
            navController.navigate(
                R.id.vendasFragment,
                null,
                NavOptions
                    .Builder()
                    .setPopUpTo(R.id.consultasFragment, true)
                    .build(),
            )
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.e("ConsultasFragment", "Erro ao navegar para vendas", e)
        }
    }

    private fun showEditClienteDialog(cliente: Cliente) {
        vendasViewModel.reloadLocais()
        NovoClienteDialog.newInstance(cliente.id).show(childFragmentManager, NovoClienteDialog.TAG)
    }

    @Suppress("DEPRECATION")
    private fun handleClienteDialogResult(bundle: Bundle) {
        val clienteId = bundle.getLong(NovoClienteDialog.RESULT_CLIENTE_ID)
        if (clienteId == NovoClienteDialog.ID_NOVO_CLIENTE) return
        val nome = bundle.getString(NovoClienteDialog.RESULT_NOME).orEmpty()
        val telefone = bundle.getString(NovoClienteDialog.RESULT_TELEFONE).orEmpty()
        val localId = bundle.getLong(NovoClienteDialog.RESULT_LOCAL_ID)
        val sublocal1Id = bundle.getOptionalLong(NovoClienteDialog.RESULT_SUBLOCAL1_ID)
        val sublocal2Id = bundle.getOptionalLong(NovoClienteDialog.RESULT_SUBLOCAL2_ID)
        val sublocal3Id = bundle.getOptionalLong(NovoClienteDialog.RESULT_SUBLOCAL3_ID)
        val fotoUri = bundle.getParcelable<Uri>(NovoClienteDialog.RESULT_FOTO_URI)
        val removerFoto = bundle.getBoolean(NovoClienteDialog.RESULT_REMOVER_FOTO)

        viewLifecycleOwner.lifecycleScope.launch {
            val cliente =
                (requireActivity().application as CadernetaApplication)
                    .clienteRepository
                    .getClienteById(clienteId)
                    ?: return@launch
            vendasViewModel.editarCliente(
                cliente = cliente,
                novoNome = nome,
                novoTelefone = telefone,
                novoLocalId = localId,
                novoSublocal1Id = sublocal1Id,
                novoSublocal2Id = sublocal2Id,
                novoSublocal3Id = sublocal3Id,
                fotoUri = fotoUri,
                removerFoto = removerFoto,
            )
        }
    }

    private fun showOpcoesExtratoDialog(
        venda: Venda,
        cliente: Cliente,
    ) {
        OpcoesExtratoDialog.newInstance(venda.id, cliente.id).show(childFragmentManager, OpcoesExtratoDialog.TAG)
    }

    private fun handleOpcoesExtratoResult(bundle: Bundle) {
        val vendaId = bundle.getLong(OpcoesExtratoDialog.RESULT_VENDA_ID)
        val clienteId = bundle.getLong(OpcoesExtratoDialog.RESULT_CLIENTE_ID)
        viewLifecycleOwner.lifecycleScope.launch {
            val app = requireActivity().application as CadernetaApplication
            val venda = app.vendaRepository.getVendaById(vendaId) ?: return@launch
            val cliente = app.clienteRepository.getClienteById(clienteId) ?: return@launch
            when (bundle.getString(OpcoesExtratoDialog.RESULT_ACTION)) {
                OpcoesExtratoDialog.ACTION_EDIT_DATE -> {
                    val novaData = java.util.Date(bundle.getLong(OpcoesExtratoDialog.RESULT_DATE_MILLIS))
                    if (viewModel.atualizarDataVenda(venda, novaData)) {
                        FeedbackPresenter.sucesso(binding.root, "Data atualizada com sucesso")
                    }
                }
                OpcoesExtratoDialog.ACTION_EDIT_OPERATION -> showEditarOperacaoDialog(venda, cliente)
                OpcoesExtratoDialog.ACTION_DELETE -> {
                    if (viewModel.excluirVenda(venda)) {
                        FeedbackPresenter.sucesso(binding.root, "Operação excluída com sucesso")
                    }
                }
            }
        }
    }

    private fun showEditarOperacaoDialog(
        venda: Venda,
        cliente: Cliente,
    ) {
        EditarOperacaoDialog.newInstance(venda.id, cliente.id).show(childFragmentManager, EditarOperacaoDialog.TAG)
    }

    private fun setupNavDrawer() {
        val toggle =
            ActionBarDrawerToggle(
                requireActivity(),
                binding.drawerLayout,
                R.string.navigation_drawer_open,
                R.string.navigation_drawer_close,
            ).apply {
                drawerArrowDrawable.color = ContextCompat.getColor(requireContext(), R.color.on_primary)
            }

        binding.drawerLayout.apply {
            addDrawerListener(toggle)
            addDrawerListener(createDrawerListener())
        }
        toggle.syncState()
    }

    private fun createDrawerListener() =
        object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(
                drawerView: View,
                slideOffset: Float,
            ) {}

            override fun onDrawerOpened(drawerView: View) = Unit

            override fun onDrawerClosed(drawerView: View) {
                binding.navView
                    .findViewById<TextInputEditText>(R.id.et_pesquisar_local)
                    .text
                    ?.clear()
            }

            override fun onDrawerStateChanged(newState: Int) {}
        }

    private fun setupLocalRecyclerView() {
        localAdapter =
            LocalConsultaAdapter(
                onLocalClick = { local ->
                    viewModel.selecionarLocal(local.id)
                    binding.etBusca.text?.clear() // Clear search field
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                },
                onToggleExpand = { local ->
                    viewModel.toggleLocalExpansion(local)
                },
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
        binding.etBusca.addTextChangedListener(
            object : TextWatcher {
                override fun beforeTextChanged(
                    s: CharSequence?,
                    start: Int,
                    count: Int,
                    after: Int,
                ) {}

                override fun onTextChanged(
                    s: CharSequence?,
                    start: Int,
                    before: Int,
                    count: Int,
                ) {}

                override fun afterTextChanged(s: Editable?) {
                    searchDebounceJob?.cancel()
                    searchDebounceJob =
                        lifecycleScope.launch {
                            kotlinx.coroutines.delay(300)
                            val query = s.toString().trim()
                            viewModel.buscarClientes(query)
                        }
                }
            },
        )
    }

    private fun setupLocalSearch() {
        binding.navView
            .findViewById<TextInputEditText>(R.id.et_pesquisar_local)
            .addTextChangedListener(
                object : TextWatcher {
                    override fun beforeTextChanged(
                        s: CharSequence?,
                        start: Int,
                        count: Int,
                        after: Int,
                    ) {}

                    override fun onTextChanged(
                        s: CharSequence?,
                        start: Int,
                        before: Int,
                        count: Int,
                    ) {}

                    override fun afterTextChanged(s: Editable?) {
                        val query = s.toString().trim()
                        viewModel.buscarLocais(query)
                    }
                },
            )
    }

    private fun initializeDrawerState() {
        binding.navView
            .findViewById<TextInputEditText>(R.id.et_pesquisar_local)
            .text
            ?.clear()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.apply {
            launch {
                viewModel.locais.collectLatest { locais ->
                    localAdapter.updateLocais(locais)
                }
            }

            launch {
                viewModel.localSelecionado.collectLatest { local ->
                    binding.tvLocalSelecionado.text = local?.nome ?: getString(R.string.todos_locais)
                }
            }

            launch {
                viewModel.clientesComSaldo.collectLatest { clientesComSaldo ->
                    binding.tvEmptyResultados.isVisible = clientesComSaldo.isEmpty()
                    resultadosAdapter.submitList(
                        clientesComSaldo.map { (cliente, saldo) ->
                            ResultadoConsulta.Cliente(cliente, saldo)
                        },
                    )
                }
            }

            launch {
                viewModel.vendasPorCliente.collectLatest { vendasPorCliente ->
                    resultadosAdapter.updateVendasPorCliente(vendasPorCliente)
                }
            }

            launch {
                viewModel.saldoAtualizado.collectLatest { clienteId ->
                    // Força uma atualização imediata do adapter
                    val currentList = resultadosAdapter.currentList.toMutableList()
                    val index =
                        currentList.indexOfFirst {
                            (it is ResultadoConsulta.Cliente && it.cliente.id == clienteId)
                        }

                    if (index != -1) {
                        val item = currentList[index]
                        if (item is ResultadoConsulta.Cliente) {
                            val novoSaldo = viewModel.getSaldoCliente(clienteId)

                            // Criar novo item com saldo atualizado
                            currentList[index] = ResultadoConsulta.Cliente(item.cliente, novoSaldo)

                            resultadosAdapter.submitList(currentList)
                        }
                    }

                    // Recarrega as vendas
                    viewModel.carregarVendasPorCliente(clienteId)
                }
            }

            launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    viewModel.eventos.collectLatest { evento ->
                        when (evento) {
                            is UiEvento.Erro -> FeedbackPresenter.erro(binding.root, evento.mensagem)
                            is UiEvento.Sucesso -> FeedbackPresenter.sucesso(binding.root, evento.mensagem)
                            is UiEvento.ConfirmarRestauracao -> Unit
                        }
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

private fun Bundle.getOptionalLong(key: String): Long? =
    if (containsKey(key)) {
        getLong(key)
    } else {
        null
    }
