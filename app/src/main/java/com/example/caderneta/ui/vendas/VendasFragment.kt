package com.example.caderneta.ui.vendas

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.R
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.databinding.FragmentVendasBinding
import com.example.caderneta.util.showErrorToast
import com.example.caderneta.util.showSuccessToast
import com.example.caderneta.viewmodel.VendasViewModel
import com.example.caderneta.viewmodel.VendasViewModelFactory
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


class VendasFragment : Fragment() {

    private var _binding: FragmentVendasBinding? = null
    private val binding get() = _binding!!

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

    private lateinit var clientesAdapter: ClientesAdapter
    private lateinit var localAdapter: LocalAdapter

    private var searchDebounceJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVendasBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fabNovoCliente.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.primary_color))
        binding.fabNovoCliente.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.on_primary))
        binding.tvLocalSelecionado.text = getString(R.string.todos_locais)

        setupRecyclerView()
        setupNavDrawer()
        setupLocalRecyclerView()
        setupListeners()
        setupSearchListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        clientesAdapter = ClientesAdapter(
            contaRepository = (requireActivity().application as CadernetaApplication).contaRepository,
            lifecycleScope = viewLifecycleOwner.lifecycleScope,
            fragmentManager = childFragmentManager,  // Passando o FragmentManager
            getClienteState = { clienteId -> vendasViewModel.getClienteState(clienteId) },
            getConfiguracoes = { vendasViewModel.configuracoes.value },
            onModoOperacaoSelected = { cliente, modoOperacao ->
                vendasViewModel.selecionarModoOperacao(cliente, modoOperacao)
            },
            onTipoTransacaoSelected = { cliente, tipoTransacao ->
                vendasViewModel.selecionarTipoTransacao(cliente, tipoTransacao)
            },
            onQuantidadeChanged = { clienteId, tipo, quantidade ->
                when (tipo) {
                    ClientesAdapter.TipoQuantidade.SALGADO ->
                        vendasViewModel.updateQuantidadeSalgados(clienteId, quantidade)
                    ClientesAdapter.TipoQuantidade.SUCO ->
                        vendasViewModel.updateQuantidadeSucos(clienteId, quantidade)
                    ClientesAdapter.TipoQuantidade.PROMO1 ->
                        vendasViewModel.updateQuantidadePromo1(clienteId, quantidade)
                    ClientesAdapter.TipoQuantidade.PROMO2 ->
                        vendasViewModel.updateQuantidadePromo2(clienteId, quantidade)
                }
            },
            onConfirmarOperacao = { clienteId ->
                vendasViewModel.confirmarOperacao(clienteId)
            },
            onCancelarOperacao = { clienteId ->
                vendasViewModel.cancelarOperacao(clienteId)
            },
            onUpdateValorTotal = { clienteId, valorTotal ->
                vendasViewModel.updateValorTotal(clienteId, valorTotal)
            },
            observeSaldoAtualizado = { observer ->
                viewLifecycleOwner.lifecycleScope.launch {
                    vendasViewModel.saldoAtualizado.collect { clienteId ->
                        observer(clienteId)
                    }
                }
            },
            onEditarCliente = { cliente ->
                showEditClienteDialog(cliente)
            },
            onExcluirCliente = { cliente ->
                vendasViewModel.excluirCliente(cliente)
            }
        )

        binding.rvClientes.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = clientesAdapter
        }
    }

    private fun setupNavDrawer() {
        val toggle = ActionBarDrawerToggle(
            requireActivity(),
            binding.drawerLayout,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
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

    private fun setupLocalRecyclerView() {
        localAdapter = LocalAdapter(
            onLocalClick = { local ->
                vendasViewModel.selecionarLocal(local.id)
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onAddSubLocal = { local -> showAddSubLocalDialog(local) },
            onEditLocal = { local -> showEditLocalDialog(local) },
            onDeleteLocal = { local -> showDeleteLocalConfirmation(local) },
            onToggleExpand = { local -> vendasViewModel.toggleLocalExpansion(local) }
        )
        binding.navView.findViewById<RecyclerView>(R.id.rv_locais).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = localAdapter
        }
    }

    private fun setupListeners() {
        binding.fabNovoCliente.setOnClickListener {
            showAddClienteDialog()
        }

        binding.navView.findViewById<TextInputLayout>(R.id.til_novo_local).setEndIconOnClickListener {
            val novoLocal = binding.navView.findViewById<TextInputEditText>(R.id.et_novo_local).text.toString()
            if (novoLocal.isNotBlank()) {
                vendasViewModel.addLocal(novoLocal)
                binding.navView.findViewById<TextInputEditText>(R.id.et_novo_local).text?.clear()
            }
        }

        binding.navView.findViewById<TextInputLayout>(R.id.til_pesquisar_local).setEndIconOnClickListener {
            val query = binding.navView.findViewById<TextInputEditText>(R.id.et_pesquisar_local).text.toString()
            vendasViewModel.searchLocais(query)
        }
    }

    private fun setupSearchListeners() {
        binding.etBusca.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchDebounceJob?.cancel()
                searchDebounceJob = lifecycleScope.launch {
                    delay(300) // Debounce de 300ms
                    val query = s?.toString()?.trim() ?: ""
                    vendasViewModel.buscarClientes(query)
                }
            }
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.clientes.collectLatest { clientes ->
                clientesAdapter.submitList(clientes)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.configuracoes.collectLatest { configuracoes ->
                Log.d("VendasFragment", "Configurações atualizadas: $configuracoes")
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.localSelecionado.collectLatest { local ->
                binding.tvLocalSelecionado.text = local?.nome ?: "Todos os locais"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.locais.collectLatest { locais ->
                Log.d("VendasFragment", "Locais atualizados: ${locais.size}")
                Log.d(
                    "VendasFragment",
                    "Locais: ${locais.map { "${it.id}:${it.nome}(${it.parentId})" }}"
                )
                localAdapter.updateLocais(locais)
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.error.collectLatest { errorMessage ->
                errorMessage?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
                    vendasViewModel.clearError()
                }
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.clienteStateUpdates.collect { clienteId ->
                val position = clientesAdapter.currentList.indexOfFirst { it.id == clienteId }
                if (position != -1) {
                    clientesAdapter.notifyItemChanged(position)
                }
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.valorTotal.collectLatest { (clienteId, valor) ->
                // Em vez de chamar updateValorTotal diretamente, vamos usar o correto payload
                clientesAdapter.notifyItemChanged(
                    clientesAdapter.currentList.indexOfFirst { it.id == clienteId },
                    ClientesAdapter.Payload.ValorTotalChanged(valor)
                )
                Log.d(
                    "VendasFragment",
                    "Valor total atualizado no Fragment: clienteId=$clienteId, valor=$valor"
                )
            }
        }


        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.operacaoConfirmada.collectLatest { operacao ->
                when (operacao) {
                    is VendasViewModel.OperacaoConfirmada.Venda -> {
                        requireContext().showSuccessToast("Venda registrada com sucesso!")
                        vendasViewModel.resetOperacaoConfirmada()
                    }

                    is VendasViewModel.OperacaoConfirmada.Pagamento -> {
                        requireContext().showSuccessToast("Pagamento registrado com sucesso!")
                        vendasViewModel.resetOperacaoConfirmada()
                    }

                    null -> {} // No operation confirmed
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            vendasViewModel.error.collectLatest { errorMessage ->
                errorMessage?.let {
                    requireContext().showErrorToast(it)
                    vendasViewModel.clearError()
                }
            }
        }
    }


    private fun showAddClienteDialog() {
        vendasViewModel.reloadLocais()
        val dialog = NovoClienteDialog(vendasViewModel)
        dialog.onClienteAdicionado = { nome, telefone, localId, sublocal1Id, sublocal2Id, sublocal3Id ->
            vendasViewModel.addCliente(nome, telefone, localId, sublocal1Id, sublocal2Id, sublocal3Id)
        }
        dialog.show(childFragmentManager, "NovoClienteDialog")
    }

    private fun showAddSubLocalDialog(parentLocal: Local) {
        val dialog = AddSubLocalDialog(parentLocal)
        dialog.onSubLocalAdicionado = { nome: String, parentId: Long ->
            vendasViewModel.addLocal(nome, parentId)
        }
        dialog.show(childFragmentManager, "AddSubLocalDialog")
    }

    private fun showEditLocalDialog(local: Local) {
        val dialog = EditLocalDialog(local)
        dialog.onLocalEditado = { localEditado ->
            vendasViewModel.editLocal(localEditado)
        }
        dialog.show(childFragmentManager, "EditLocalDialog")
    }

    private fun showDeleteLocalConfirmation(local: Local) {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir Local")
            .setMessage("Tem certeza que deseja excluir ${local.nome}?")
            .setPositiveButton("Sim") { _, _ -> vendasViewModel.deleteLocal(local) }
            .setNegativeButton("Não", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        searchDebounceJob?.cancel()
        _binding = null
    }
}

class EditLocalDialog(private val local: Local) : DialogFragment() {
    var onLocalEditado: ((Local) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Editar Local")
            .setView(R.layout.dialog_edit_local)
            .setPositiveButton("Salvar") { _, _ ->
                val novoNome = dialog?.findViewById<EditText>(R.id.et_nome_local)?.text.toString()
                if (novoNome.isNotBlank()) {
                    onLocalEditado?.invoke(local.copy(nome = novoNome))
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.findViewById<EditText>(R.id.et_nome_local)?.setText(local.nome)
    }
}

class AddSubLocalDialog(private val parentLocal: Local) : DialogFragment() {
    var onSubLocalAdicionado: ((String, Long) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return AlertDialog.Builder(requireContext())
            .setTitle("Adicionar Sublocal")
            .setView(R.layout.dialog_add_sublocal)
            .setPositiveButton("Adicionar") { _, _ ->
                val nomeSublocal = dialog?.findViewById<EditText>(R.id.et_nome_sublocal)?.text.toString()
                if (nomeSublocal.isNotBlank()) {
                    onSubLocalAdicionado?.invoke(nomeSublocal, parentLocal.id)
                }
            }
            .setNegativeButton("Cancelar", null)
            .create()
    }
}