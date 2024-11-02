package com.example.caderneta.ui.consultas

import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.R
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
import com.google.android.material.button.MaterialButton
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
            .setPositiveButton("Salvar", null)
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
        setupClienteInfo()
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
            btnVenda.apply {
                insets(0)
                setupButtonBase(this)
                setOnClickListener {
                    vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.VENDA)
                }
            }

            btnPromocao.apply {
                insets(0)
                setupButtonBase(this)
                setOnClickListener {
                    vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.PROMOCAO)
                }
            }

            btnPagamento.apply {
                insets(0)
                setupButtonBase(this)
                setOnClickListener {
                    vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.PAGAMENTO)
                }
            }

            listOf(btnAVista, btnAPrazo).forEach { button ->
                button.apply {
                    insets(0)
                    setupButtonBase(this)
                }
            }

            btnAVista.setOnClickListener {
                vendasViewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_VISTA)
            }

            btnAPrazo.setOnClickListener {
                vendasViewModel.selecionarTipoTransacao(cliente, TipoTransacao.A_PRAZO)
            }

            layoutPagamento.visibility = View.GONE
            btnTudo.visibility = View.GONE
            btnConfirmarPagamento.visibility = View.GONE
            btnCancelarPagamento.visibility = View.GONE
            btnConfirmarOperacao.visibility = View.GONE
            btnCancelarOperacao.visibility = View.GONE
        }
    }

    private fun MaterialButton.insets(value: Int) {
        insetTop = value
        insetBottom = value

    }


    private fun setupButtonBase(button: MaterialButton) {
        button.apply {
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            iconPadding = 0
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            strokeWidth = 1
            val buttonStrokeColor = ContextCompat.getColor(context, R.color.button_stroke)
            strokeColor = ColorStateList.valueOf(buttonStrokeColor)

            // Definir estilo inicial com um Drawable compatível
            val unselectedColor = ContextCompat.getColor(context, R.color.button_background_unselected)
            setBackgroundColor(unselectedColor)
            val unselectedIconColor = ContextCompat.getColor(context, R.color.button_icon_unselected)
            setIconTint(ColorStateList.valueOf(unselectedIconColor))
            elevation = 0f
        }
    }

    private fun setupValueFields() {
        binding.apply {
            etValorTotal.isEnabled = false
            etValorTotalPromocao.isEnabled = false
        }
    }

    private fun setupClienteInfo() {
        binding.apply {
            tvNomeCliente.text = cliente.nome
            viewLifecycleOwner.lifecycleScope.launch {
                val saldo = consultasViewModel.getSaldoCliente(cliente.id)
                tvValorDevido.text = String.format("R$ %.2f", saldo)
                tvValorDevido.setTextColor(
                    if (saldo > 0) requireContext().getColor(android.R.color.holo_red_dark)
                    else requireContext().getColor(android.R.color.holo_green_dark)
                )
            }
        }
    }

    private fun initializeOperation() {
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

        vendasViewModel.setInitialState(cliente.id, initialState)
        viewLifecycleOwner.lifecycleScope.launch {
            consultasViewModel.abrirEdicaoOperacao(venda)
        }
    }

    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vendasViewModel.clienteStates.collectLatest { states ->
                        states[cliente.id]?.let { state ->
                            updateUI(state)
                            consultasViewModel.updateClienteState(state)
                        }
                    }
                }

                launch {
                    vendasViewModel.configuracoes.collectLatest { config ->
                        config?.let {
                            binding.layoutVendaPromocao.visibility =
                                if (it.promocoesAtivadas) View.VISIBLE else View.GONE
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

                launch {
                    vendasViewModel.error.collectLatest { error ->
                        error?.let {
                            requireContext().showErrorToast(it)
                            vendasViewModel.clearError()
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(state: VendasViewModel.ClienteState) {
        binding.apply {
            btnVenda.isSelected = state.modoOperacao == ModoOperacao.VENDA
            btnPromocao.isSelected = state.modoOperacao == ModoOperacao.PROMOCAO
            btnPagamento.isSelected = state.modoOperacao == ModoOperacao.PAGAMENTO

            updateButtonStyle(btnVenda)
            updateButtonStyle(btnPromocao)
            updateButtonStyle(btnPagamento)

            layoutVenda.visibility = when (state.modoOperacao) {
                ModoOperacao.VENDA, ModoOperacao.PROMOCAO -> View.VISIBLE
                else -> View.GONE
            }

            layoutPagamento.visibility = View.GONE

            if (state.modoOperacao != ModoOperacao.PAGAMENTO) {
                layoutBotoesVenda.visibility = View.VISIBLE
                btnAVista.isSelected = state.tipoTransacao == TipoTransacao.A_VISTA
                btnAPrazo.isSelected = state.tipoTransacao == TipoTransacao.A_PRAZO

                updateButtonStyle(btnAVista)
                updateButtonStyle(btnAPrazo)
            } else {
                layoutBotoesVenda.visibility = View.GONE
            }

            layoutVendaNormal.visibility = when {
                state.modoOperacao == ModoOperacao.VENDA && state.tipoTransacao != null -> View.VISIBLE
                else -> View.GONE
            }

            layoutVendaPromocao.visibility = when {
                state.modoOperacao == ModoOperacao.PROMOCAO && state.tipoTransacao != null -> View.VISIBLE
                else -> View.GONE
            }

            updateQuantidades(state)
            updateValores(state)
        }
    }

    private fun updateButtonStyle(button: MaterialButton) {
        if (button.isSelected) {
            button.apply {
                val selectedColor = ContextCompat.getColor(context, R.color.button_background_selected)
                setBackgroundColor(selectedColor)
                val selectedIconColor = ContextCompat.getColor(context, R.color.button_icon_selected)
                setIconTint(ColorStateList.valueOf(selectedIconColor))
                elevation = 8f
            }
        } else {
            button.apply {
                val unselectedColor = ContextCompat.getColor(context, R.color.button_background_unselected)
                setBackgroundColor(unselectedColor)
                val unselectedIconColor = ContextCompat.getColor(context, R.color.button_icon_unselected)
                setIconTint(ColorStateList.valueOf(unselectedIconColor))
                elevation = 0f
            }
        }
    }

    private fun updateQuantidades(state: VendasViewModel.ClienteState) {
        contadorSalgadosHelper?.setQuantidade(state.quantidadeSalgados)
        contadorSucosHelper?.setQuantidade(state.quantidadeSucos)
        contadorPromo1Helper?.setQuantidade(state.quantidadePromo1)
        contadorPromo2Helper?.setQuantidade(state.quantidadePromo2)
    }

    private fun updateValores(state: VendasViewModel.ClienteState) {
        binding.apply {
            when (state.modoOperacao) {
                ModoOperacao.VENDA -> etValorTotal.setText(String.format("%.2f", state.valorTotal))
                ModoOperacao.PROMOCAO -> etValorTotalPromocao.setText(String.format("%.2f", state.valorTotal))
                else -> {}
            }
        }
    }

    override fun onStart() {
        super.onStart()
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