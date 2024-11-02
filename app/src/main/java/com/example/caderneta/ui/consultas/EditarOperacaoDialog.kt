package com.example.caderneta.ui.consultas

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.ItemClienteBinding
import com.example.caderneta.util.ContadorHelper
import com.example.caderneta.viewmodel.VendasViewModel
import com.example.caderneta.viewmodel.VendasViewModelFactory
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

    private val viewModel: VendasViewModel by viewModels {
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

    override fun onStart() {
        super.onStart()
        setupUI()
        observeViewModel()
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
                viewModel.updateQuantidadeSalgados(cliente.id, quantidade)
            }
        }

        contadorSucosHelper = ContadorHelper(binding.contadorSucos.root).apply {
            setOnQuantidadeChangedListener { quantidade ->
                viewModel.updateQuantidadeSucos(cliente.id, quantidade)
            }
        }

        contadorPromo1Helper = ContadorHelper(binding.contadorPromo1.root).apply {
            setOnQuantidadeChangedListener { quantidade ->
                viewModel.updateQuantidadePromo1(cliente.id, quantidade)
            }
        }

        contadorPromo2Helper = ContadorHelper(binding.contadorPromo2.root).apply {
            setOnQuantidadeChangedListener { quantidade ->
                viewModel.updateQuantidadePromo2(cliente.id, quantidade)
            }
        }
    }

    private fun setupButtons() {
        binding.apply {
            // Configurar listeners para botões principais
            btnVenda.setOnClickListener {
                viewModel.selecionarModoOperacao(cliente, ModoOperacao.VENDA)
            }
            btnPromocao.setOnClickListener {
                viewModel.selecionarModoOperacao(cliente, ModoOperacao.PROMOCAO)
            }
            btnPagamento.setOnClickListener {
                viewModel.selecionarModoOperacao(cliente, ModoOperacao.PAGAMENTO)
            }

            // Configurar listeners para botões de tipo de transação
            btnAVista.setOnClickListener {
                viewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_VISTA)
            }
            btnAPrazo.setOnClickListener {
                viewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_PRAZO)
            }
        }
    }

    private fun setupValueFields() {
        binding.etValorPagamento.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val valor = s.toString().toDoubleOrNull() ?: 0.0
                viewModel.updateValorTotal(cliente.id, valor)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.clienteStates.collectLatest { states ->
                states[cliente.id]?.let { state ->
                    updateUI(state)
                }
            }
        }
    }

    private fun updateUI(state: VendasViewModel.ClienteState) {
        binding.apply {
            // Atualizar visibilidade dos layouts
            layoutVenda.visibility = if (state.modoOperacao != null &&
                state.modoOperacao != ModoOperacao.PAGAMENTO)
                android.view.View.VISIBLE else android.view.View.GONE

            layoutPagamento.visibility = if (state.modoOperacao == ModoOperacao.PAGAMENTO)
                android.view.View.VISIBLE else android.view.View.GONE

            // Atualizar seleção dos botões
            btnVenda.isSelected = state.modoOperacao == ModoOperacao.VENDA
            btnPromocao.isSelected = state.modoOperacao == ModoOperacao.PROMOCAO
            btnPagamento.isSelected = state.modoOperacao == ModoOperacao.PAGAMENTO

            // Atualizar tipo de transação
            btnAVista.isSelected = state.tipoTransacao == TipoTransacao.A_VISTA
            btnAPrazo.isSelected = state.tipoTransacao == TipoTransacao.A_PRAZO

            // Atualizar valores
            if (state.modoOperacao == ModoOperacao.PAGAMENTO) {
                etValorPagamento.setText(state.valorTotal.toString())
            } else {
                etValorTotal.setText(state.valorTotal.toString())
            }
        }
    }

    private fun initializeOperation() {
        // Inicializar estado com base na venda atual
        val initialState = VendasViewModel.ClienteState(
            clienteId = cliente.id,
            modoOperacao = when {
                venda.isPromocao -> ModoOperacao.PROMOCAO
                venda.transacao == "pagamento" -> ModoOperacao.PAGAMENTO
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
        viewModel.setInitialState(cliente.id, initialState)
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