package com.example.caderneta.ui.consultas

import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
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
            .setTitle(getTitleForOperation())
            .setView(binding.root)
            .setPositiveButton("Salvar", null)
            .setNegativeButton("Cancelar", null)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
            }
    }

    private fun getTitleForOperation(): String {
        return when {
            venda.transacao == "pagamento" -> "Editar Pagamento"
            venda.isPromocao -> "Editar Promoção"
            else -> "Editar Venda"
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
        configureInitialLayout()
        setupUI()
        observeViewModels()
        initializeOperation()
    }

    private fun configureInitialLayout() {
        when (venda.transacao) {
            "pagamento" -> configurePagamentoLayout()
            else -> configureVendaLayout()
        }
    }

    private fun configurePagamentoLayout() {
        binding.apply {
            // Ocultar elementos de venda/promoção
            btnVenda.visibility = View.GONE
            btnPromocao.visibility = View.GONE
            btnPagamento.visibility = View.GONE
            layoutBotoesVenda.visibility = View.GONE
            layoutVendaNormal.visibility = View.GONE
            layoutVendaPromocao.visibility = View.GONE
            layoutBotoesConfirmacao.visibility = View.GONE

            // Remover legendas acessando os LinearLayouts diretamente
            (layoutBotoesPrincipais.getChildAt(0) as? LinearLayout)?.let { linearLayout ->
                linearLayout.getChildAt(1)?.visibility = View.GONE // Legenda "Venda"
            }
            (layoutBotoesPrincipais.getChildAt(1) as? LinearLayout)?.let { linearLayout ->
                linearLayout.getChildAt(1)?.visibility = View.GONE // Legenda "Promoção"
            }
            (layoutBotoesPrincipais.getChildAt(2) as? LinearLayout)?.let { linearLayout ->
                linearLayout.getChildAt(1)?.visibility = View.GONE // Legenda "Pagamento"
            }

            // Configurar layout de pagamento
            layoutPagamento.visibility = View.VISIBLE
            etValorPagamento.setText(String.format("%.2f", venda.valor))
            etValorPagamento.isEnabled = true

            // Remover botões de confirmação/cancelamento do layout de pagamento
            layoutBotoesConfirmacaoPagamento.visibility = View.GONE

            // Setup do botão Tudo
            viewLifecycleOwner.lifecycleScope.launch {
                val saldo = consultasViewModel.getSaldoCliente(cliente.id)
                btnTudo.apply {
                    visibility = View.VISIBLE
                    isEnabled = saldo > 0
                    setOnClickListener {
                        etValorPagamento.setText(String.format("%.2f", saldo))
                        vendasViewModel.updateValorTotal(cliente.id, saldo)
                    }
                }
            }
        }
    }

    private fun configureVendaLayout() {
        binding.apply {
            // Configurar visibilidade dos elementos
            btnVenda.visibility = View.VISIBLE
            btnPromocao.visibility = View.VISIBLE
            btnPagamento.visibility = View.GONE
            layoutPagamento.visibility = View.GONE

            // Remover apenas a legenda "Pagamento", mantendo as outras
            (layoutBotoesPrincipais.getChildAt(2) as? LinearLayout)?.let { linearLayout ->
                linearLayout.getChildAt(1)?.visibility = View.GONE // Legenda "Pagamento"
            }

            // Configurar estado inicial dos botões
            btnVenda.isSelected = !venda.isPromocao
            btnPromocao.isSelected = venda.isPromocao

            layoutBotoesVenda.visibility = View.VISIBLE
            btnAVista.isSelected = venda.transacao == "a_vista"
            btnAPrazo.isSelected = venda.transacao == "a_prazo"

            // Atualizar estilos dos botões
            updateButtonStyle(btnVenda)
            updateButtonStyle(btnPromocao)
            updateButtonStyle(btnAVista)
            updateButtonStyle(btnAPrazo)

            // Configurar layouts específicos
            layoutVendaNormal.visibility = if (!venda.isPromocao) View.VISIBLE else View.GONE
            layoutVendaPromocao.visibility = if (venda.isPromocao) View.VISIBLE else View.GONE
        }
    }

    private fun setupUI() {
        setupContadores()
        setupButtons()
        setupValueFields()
        setupClienteInfo()
    }

    private fun setupContadores() {
        if (venda.transacao != "pagamento") {
            contadorSalgadosHelper = ContadorHelper(binding.contadorSalgados.root).apply {
                setOnQuantidadeChangedListener { quantidade ->
                    vendasViewModel.updateQuantidadeSalgados(cliente.id, quantidade)
                }
                setQuantidade(venda.quantidadeSalgados)
            }

            contadorSucosHelper = ContadorHelper(binding.contadorSucos.root).apply {
                setOnQuantidadeChangedListener { quantidade ->
                    vendasViewModel.updateQuantidadeSucos(cliente.id, quantidade)
                }
                setQuantidade(venda.quantidadeSucos)
            }

            if (venda.isPromocao) {
                setupPromoContadores()
            }
        }
    }

    private fun setupPromoContadores() {
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
            if (venda.transacao != "pagamento") {
                btnVenda.apply {
                    insets(0)
                    setupButtonBase(this)
                    setOnClickListener {
                        if (!venda.isPromocao) {
                            vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.VENDA)
                        }
                    }
                }

                btnPromocao.apply {
                    insets(0)
                    setupButtonBase(this)
                    setOnClickListener {
                        if (venda.isPromocao) {
                            vendasViewModel.selecionarModoOperacao(cliente, ModoOperacao.PROMOCAO)
                        }
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
            }

            // Ocultar botões não necessários
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
            val unselectedColor = ContextCompat.getColor(context, R.color.button_background_unselected)
            setBackgroundColor(unselectedColor)
            val unselectedIconColor = ContextCompat.getColor(context, R.color.button_icon_unselected)
            setIconTint(ColorStateList.valueOf(unselectedIconColor))
            elevation = 0f
        }
    }

    private fun setupValueFields() {
        binding.apply {
            if (venda.transacao == "pagamento") {
                etValorPagamento.apply {
                    setText(String.format("%.2f", venda.valor))
                    isEnabled = true
                }
            } else {
                etValorTotal.isEnabled = false
                etValorTotalPromocao.isEnabled = false
            }
        }
    }

    private fun setupClienteInfo() {
        binding.apply {
            tvNomeCliente.text = cliente.nome
            viewLifecycleOwner.lifecycleScope.launch {
                val saldo = consultasViewModel.getSaldoCliente(cliente.id)
                tvValorDevido.text = String.format("R$ %.2f", saldo)
                tvValorDevido.setTextColor(
                    if (saldo > 0) ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    else ContextCompat.getColor(requireContext(), android.R.color.holo_green_dark)
                )
            }
        }
    }

    private fun initializeOperation() {
        val initialState = VendasViewModel.ClienteState(
            clienteId = cliente.id,
            modoOperacao = when {
                venda.transacao == "pagamento" -> ModoOperacao.PAGAMENTO
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
                            if (venda.transacao != "pagamento") {
                                updateUI(state)
                            }
                            consultasViewModel.updateClienteState(state)
                        }
                    }
                }

                launch {
                    vendasViewModel.configuracoes.collectLatest { config ->
                        config?.let {
                            if (venda.isPromocao) {
                                binding.layoutVendaPromocao.visibility =
                                    if (it.promocoesAtivadas) View.VISIBLE else View.GONE
                            }
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

            updateButtonStyle(btnVenda)
            updateButtonStyle(btnPromocao)

            layoutVenda.visibility = when (state.modoOperacao) {
                ModoOperacao.VENDA, ModoOperacao.PROMOCAO -> View.VISIBLE
                else -> View.GONE
            }

            layoutBotoesVenda.visibility = View.VISIBLE
            btnAVista.isSelected = state.tipoTransacao == TipoTransacao.A_VISTA
            btnAPrazo.isSelected = state.tipoTransacao == TipoTransacao.A_PRAZO

            updateButtonStyle(btnAVista)
            updateButtonStyle(btnAPrazo)

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
        if (venda.transacao != "pagamento") {
            contadorSalgadosHelper?.setQuantidade(state.quantidadeSalgados)
            contadorSucosHelper?.setQuantidade(state.quantidadeSucos)
            if (venda.isPromocao) {
                contadorPromo1Helper?.setQuantidade(state.quantidadePromo1)
                contadorPromo2Helper?.setQuantidade(state.quantidadePromo2)
            }
        }
    }

    private fun updateValores(state: VendasViewModel.ClienteState) {
        binding.apply {
            when {
                venda.transacao == "pagamento" -> {
                    etValorPagamento.setText(String.format("%.2f", state.valorTotal))
                }
                state.modoOperacao == ModoOperacao.VENDA -> {
                    etValorTotal.setText(String.format("%.2f", state.valorTotal))
                }
                state.modoOperacao == ModoOperacao.PROMOCAO -> {
                    etValorTotalPromocao.setText(String.format("%.2f", state.valorTotal))
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    when (venda.transacao) {
                        "pagamento" -> {
                            val valorPagamento = binding.etValorPagamento.text.toString().toDoubleOrNull()
                            if (valorPagamento != null && valorPagamento > 0) {
                                vendasViewModel.updateValorTotal(cliente.id, valorPagamento)
                            }
                        }
                        else -> {
                            // Valores já atualizados pelos contadores e estado
                        }
                    }

                    if (consultasViewModel.confirmarEdicaoOperacao(venda)) {
                        requireContext().showSuccessToast(
                            when (venda.transacao) {
                                "pagamento" -> "Pagamento atualizado com sucesso"
                                else -> "Operação atualizada com sucesso"
                            }
                        )
                        dismiss()
                    }
                } catch (e: Exception) {
                    requireContext().showErrorToast("Erro ao atualizar operação: ${e.message}")
                }
            }
        }

        // Configurar tamanho do dialog
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
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