package com.example.caderneta.ui.consultas

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.ItemClienteBinding
import com.example.caderneta.util.ContadorHelper
import com.example.caderneta.util.showErrorToast
import com.example.caderneta.util.showSuccessToast
import com.example.caderneta.viewmodel.ConsultasViewModel
import com.example.caderneta.viewmodel.ConsultasViewModelFactory
import com.example.caderneta.viewmodel.VendasViewModel
import com.example.caderneta.viewmodel.VendasViewModelFactory
import com.google.android.gms.tasks.Tasks.await
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class EditarOperacaoDialog(
    private val venda: Venda,
    private val cliente: Cliente
) : DialogFragment() {

    private var _binding: ItemClienteBinding? = null
    private val binding get() = _binding!!

    private var contadorSalgadosHelper: ContadorHelper? = null
    private var contadorSucosHelper: ContadorHelper? = null
    private var contadorPromo1Helper: ContadorHelper? = null
    private var contadorPromo2Helper: ContadorHelper? = null

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

    private val consultasViewModel: ConsultasViewModel by viewModels {
        ConsultasViewModelFactory(
            (requireActivity().application as CadernetaApplication).clienteRepository,
            (requireActivity().application as CadernetaApplication).vendaRepository,
            (requireActivity().application as CadernetaApplication).localRepository,
            (requireActivity().application as CadernetaApplication).contaRepository,
            (requireActivity().application as CadernetaApplication).operacaoRepository
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = ItemClienteBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext())
            .setTitle("Editar Operação")
            .setView(binding.root)
            .setPositiveButton("Salvar", null) // Será configurado no onStart
            .setNegativeButton("Cancelar", null)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
            }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI()
        observeViewModels()
        initializeOperation()
    }

    private fun setupUI() {
        setupContadores()
        setupButtons()
        setupValueFields()
    }

    private fun setupContadores() {
        contadorSalgadosHelper = ContadorHelper(binding.contadorSalgados.root).apply {
            setOnQuantidadeChangedListener { quantidade ->
                vendasViewModel.updateQuantidadeSalgados(cliente.id, quantidade)
            }
        }

        contadorSucosHelper = ContadorHelper(binding.contadorSucos.root).apply {
            setOnQuantidadeChangedListener { quantidade ->
                vendasViewModel.updateQuantidadeSucos(cliente.id, quantidade)
            }
        }

        contadorPromo1Helper = ContadorHelper(binding.contadorPromo1.root).apply {
            setOnQuantidadeChangedListener { quantidade ->
                vendasViewModel.updateQuantidadePromo1(cliente.id, quantidade)
            }
        }

        contadorPromo2Helper = ContadorHelper(binding.contadorPromo2.root).apply {
            setOnQuantidadeChangedListener { quantidade ->
                vendasViewModel.updateQuantidadePromo2(cliente.id, quantidade)
            }
        }
    }

    private fun setupButtons() {
        binding.apply {
            // Configurar listeners para botões principais
            btnVenda.setOnClickListener {
                vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.VENDA)
            }
            btnPromocao.setOnClickListener {
                vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.PROMOCAO)
            }
            btnPagamento.setOnClickListener {
                vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.PAGAMENTO)
            }

            // Configurar listeners para botões de tipo de transação
            btnAVista.setOnClickListener {
                vendasViewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_VISTA)
            }
            btnAPrazo.setOnClickListener {
                vendasViewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_PRAZO)
            }

            // Configurar visibilidade inicial
            etValorPagamento.visibility = View.GONE
            btnTudo.visibility = View.GONE
            btnConfirmarPagamento.visibility = View.GONE
            btnCancelarPagamento.visibility = View.GONE
        }
    }

    private fun setupValueFields() {
        binding.etValorPagamento.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val valor = s.toString().toDoubleOrNull() ?: 0.0
                vendasViewModel.updateValorTotal(cliente.id, valor)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vendasViewModel.clienteStates.collectLatest { states ->
                        states[cliente.id]?.let { state ->
                            updateUI(state)
                        }
                    }
                }

                launch {
                    vendasViewModel.error.collectLatest { error ->
                        error?.let {
                            requireContext().showErrorToast(it)
                            vendasViewModel.clearError()
                        }
                    }
                }

                launch {
                    consultasViewModel.error.collectLatest { error ->
                        error?.let {
                            requireContext().showErrorToast(it)
                            consultasViewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(state: VendasViewModel.ClienteState) {
        binding.apply {
            // Atualizar visibilidade dos layouts
            layoutVenda.visibility = if (state.modoOperacao != null &&
                state.modoOperacao != ModoOperacao.PAGAMENTO)
                View.VISIBLE else View.GONE

            layoutPagamento.visibility = View.GONE // Desabilitar pagamento na edição

            // Atualizar seleção dos botões
            btnVenda.isSelected = state.modoOperacao == ModoOperacao.VENDA
            btnPromocao.isSelected = state.modoOperacao == ModoOperacao.PROMOCAO
            btnPagamento.isSelected = false // Desabilitar pagamento na edição

            // Atualizar tipo de transação
            btnAVista.isSelected = state.tipoTransacao == TipoTransacao.A_VISTA
            btnAPrazo.isSelected = state.tipoTransacao == TipoTransacao.A_PRAZO

            // Atualizar valores
            if (state.modoOperacao != ModoOperacao.PAGAMENTO) {
                etValorTotal.setText(state.valorTotal.toString())
            }

            // Atualizar contadores
            contadorSalgadosHelper?.setQuantidade(state.quantidadeSalgados)
            contadorSucosHelper?.setQuantidade(state.quantidadeSucos)
            contadorPromo1Helper?.setQuantidade(state.quantidadePromo1)
            contadorPromo2Helper?.setQuantidade(state.quantidadePromo2)
        }
    }

    private fun initializeOperation() {
        // Inicializar estado com base na venda atual
        val initialState = VendasViewModel.ClienteState(
            clienteId = cliente.id,
            modoOperacao = when {
                venda.isPromocao -> ModoOperacao.PROMOCAO
                else -> ModoOperacao.VENDA
            },
            tipoTransacao = when (venda.transacao) {
                "a_vista" -> TipoTransacao.A_VISTA
                "a_prazo" -> TipoTransacao.A_PRAZO
                else -> null
            },
            quantidadeSalgados = venda.quantidadeSalgados,
            quantidadeSucos = venda.quantidadeSucos,
            valorTotal = venda.valor
        )

        // Aplicar estado inicial
        vendasViewModel.setInitialState(cliente.id, initialState)
    }

    override fun onStart() {
        super.onStart()

        // Configurar o botão positivo para salvar as alterações
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    if (consultasViewModel.confirmarEdicaoOperacao(venda)) {
                        requireContext().showSuccessToast("Operação atualizada com sucesso")
                        dismiss()
                    }
                } catch (e: Exception) {
                    requireContext().showErrorToast("Erro ao atualizar operação: ${e.message}")
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        contadorSalgadosHelper = null
        contadorSucosHelper = null
        contadorPromo1Helper = null
        contadorPromo2Helper = null
        _binding = null
    }

    companion object {
        const val TAG = "EditarOperacaoDialog"
    }
}