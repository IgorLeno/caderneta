package com.example.caderneta.ui.vendas

import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.caderneta.R
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.databinding.ItemClienteBinding
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.util.ContadorHelper
import com.example.caderneta.util.centavosParaReais
import com.example.caderneta.util.centavosParaTextoDecimal
import com.example.caderneta.viewmodel.ClienteState
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

@Suppress("LongParameterList")
class ClientesAdapter(
    private val contaRepository: ContaRepository,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val fragmentManager: androidx.fragment.app.FragmentManager, // Novo parâmetro
    private val getClienteState: (Long) -> ClienteState?,
    private val getConfiguracoes: () -> Configuracoes?,
    private val onModoOperacaoSelected: (Cliente, ModoOperacao?) -> Unit,
    private val onTipoTransacaoSelected: (Cliente, TipoTransacao?) -> Unit,
    private val onQuantidadeChanged: (Long, TipoQuantidade, Int) -> Unit,
    private val onConfirmarOperacao: (Long) -> Unit,
    private val onCancelarOperacao: (Long) -> Unit,
    private val onUpdateValorTotal: (Long, Long) -> Unit,
    private val observeSaldoAtualizado: ((suspend (Long) -> Unit) -> Unit),
    private val getClientePhotoFile: (String?) -> File?,
) : ListAdapter<Cliente, ClientesAdapter.ClienteViewHolder>(ClienteDiffCallback()) {
    enum class TipoQuantidade {
        SALGADO,
        SUCO,
        PROMO1,
        PROMO2,
    }

    init {
        lifecycleScope.launch {
            observeSaldoAtualizado { clienteId ->
                val position = currentList.indexOfFirst { it.id == clienteId }
                if (position != -1) {
                    notifyItemChanged(position, Payload.SaldoChanged)
                }
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ClienteViewHolder {
        val binding = ItemClienteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClienteViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ClienteViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: ClienteViewHolder,
        position: Int,
        payloads: List<Any>,
    ) {
        if (payloads.isNotEmpty()) {
            when (payloads[0]) {
                is Payload.ValorTotalChanged -> holder.updateValorTotal(payloads[0] as Payload.ValorTotalChanged)
                is Payload.SaldoChanged -> holder.updateSaldo()
                is Payload.StateChanged -> holder.updateStateFromPayload()
                is Payload.ConfiguracoesChanged -> holder.updateStateFromPayload()
                else -> super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onViewRecycled(holder: ClienteViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelSaldoJob()
        holder.cleanupContadores()
    }

    inner class ClienteViewHolder(
        private val binding: ItemClienteBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var contadorSalgadosHelper: ContadorHelper? = null
        private var contadorSucosHelper: ContadorHelper? = null
        private var contadorPromo1Helper: ContadorHelper? = null
        private var contadorPromo2Helper: ContadorHelper? = null
        private var boundClienteId: Long? = null
        private var boundCliente: Cliente? = null
        private var saldoJob: Job? = null
        private var isBindingPagamento = false
        private var sincronizandoEstado = false
        private val pagamentoInputController = PagamentoInputController()

        init {
            setupPagamentoWatcher()
            setupToggleGroupListeners()
        }

        fun bind(cliente: Cliente) {
            boundClienteId = cliente.id
            boundCliente = cliente
            cancelSaldoJob()
            setupBasicInfo(cliente)
            setupContadores(cliente)
            setupPagamentoLayout(cliente)
            setupConfirmationButtons(cliente)
            updateButtonStates(cliente)
            setupButtonStateListeners()
            setupMenuOpcoes(cliente)
        }

        fun cleanupContadores() {
            contadorSalgadosHelper = null
            contadorSucosHelper = null
            contadorPromo1Helper = null
            contadorPromo2Helper = null
            boundCliente = null
        }

        fun cancelSaldoJob() {
            saldoJob?.cancel()
            saldoJob = null
        }

        private fun setupBasicInfo(cliente: Cliente) {
            val fotoSizePx = binding.ivClienteFoto.fixedImageSizePx()
            binding.tvNomeCliente.text = cliente.nome
            binding.ivClienteFoto.contentDescription =
                itemView.context.getString(R.string.cliente_photo_content_description, cliente.nome)
            binding.ivClienteFoto.load(getClientePhotoFile(cliente.fotoNome)) {
                size(fotoSizePx, fotoSizePx)
                placeholder(R.drawable.ic_avatar)
                error(R.drawable.ic_avatar)
                transformations(CircleCropTransformation())
            }
            updateSaldo(cliente.id)
        }

        fun updateSaldo() {
            boundClienteId?.let(::updateSaldo)
        }

        fun updateStateFromPayload() {
            boundCliente?.let(::updateButtonStates)
        }

        private fun updateSaldo(clienteId: Long) {
            cancelSaldoJob()
            saldoJob =
                lifecycleScope.launch {
                    val saldo = contaRepository.getContaByCliente(clienteId)?.saldoCentavos ?: 0
                    if (boundClienteId != clienteId) return@launch
                    binding.tvValorDevido.text = saldo.centavosParaReais()
                    binding.tvValorDevido.setTextColor(
                        ContextCompat.getColor(
                            itemView.context,
                            if (saldo > 0) R.color.delete_color else R.color.add_color,
                        ),
                    )
                }
        }

        private fun setupContadores(cliente: Cliente) {
            contadorSalgadosHelper =
                ContadorHelper(binding.contadorSalgados.root).apply {
                    setOnQuantidadeChangedListener { quantidade ->
                        onQuantidadeChanged(cliente.id, TipoQuantidade.SALGADO, quantidade)
                    }
                }

            contadorSucosHelper =
                ContadorHelper(binding.contadorSucos.root).apply {
                    setOnQuantidadeChangedListener { quantidade ->
                        onQuantidadeChanged(cliente.id, TipoQuantidade.SUCO, quantidade)
                    }
                }

            contadorPromo1Helper =
                ContadorHelper(binding.contadorPromo1.root).apply {
                    setOnQuantidadeChangedListener { quantidade ->
                        onQuantidadeChanged(cliente.id, TipoQuantidade.PROMO1, quantidade)
                    }
                }

            contadorPromo2Helper =
                ContadorHelper(binding.contadorPromo2.root).apply {
                    setOnQuantidadeChangedListener { quantidade ->
                        onQuantidadeChanged(cliente.id, TipoQuantidade.PROMO2, quantidade)
                    }
                }

            // Atualizar valores iniciais
            val state = getClienteState(cliente.id)
            if (state != null) {
                contadorSalgadosHelper?.setQuantidade(state.quantidadeSalgados)
                contadorSucosHelper?.setQuantidade(state.quantidadeSucos)
                contadorPromo1Helper?.setQuantidade(state.quantidadePromo1)
                contadorPromo2Helper?.setQuantidade(state.quantidadePromo2)
            }
        }

        private fun setupToggleGroupListeners() {
            binding.toggleGroupModo.addOnButtonCheckedListener { _, checkedId, isChecked ->
                onModoToggled(checkedId, isChecked)
            }
            binding.toggleGroupTipo.addOnButtonCheckedListener { _, checkedId, isChecked ->
                onTipoToggled(checkedId, isChecked)
            }
        }

        private fun onModoToggled(
            checkedId: Int,
            isChecked: Boolean,
        ) {
            if (sincronizandoEstado) return
            val cliente = boundCliente ?: return
            onModoOperacaoSelected(cliente, modoOperacaoParaBotao(checkedId, isChecked))
            updateButtonStates(cliente)
        }

        private fun modoOperacaoParaBotao(
            checkedId: Int,
            isChecked: Boolean,
        ): ModoOperacao? {
            if (!isChecked) return null
            return when (checkedId) {
                R.id.btnVenda -> ModoOperacao.VENDA
                R.id.btnPromocao -> if (getConfiguracoes()?.promocoesAtivadas == true) ModoOperacao.PROMOCAO else null
                R.id.btnPagamento -> ModoOperacao.PAGAMENTO
                else -> null
            }
        }

        private fun onTipoToggled(
            checkedId: Int,
            isChecked: Boolean,
        ) {
            if (sincronizandoEstado) return
            val cliente = boundCliente ?: return
            val novoTipoTransacao =
                when {
                    !isChecked -> null
                    checkedId == R.id.btnAVista -> TipoTransacao.A_VISTA
                    checkedId == R.id.btnAPrazo -> TipoTransacao.A_PRAZO
                    else -> null
                }
            onTipoTransacaoSelected(cliente, novoTipoTransacao)
            updateButtonStates(cliente)
        }

        private fun setupPagamentoLayout(cliente: Cliente) {
            binding.apply {
                btnTudo.setOnClickListener {
                    lifecycleScope.launch {
                        val saldo = contaRepository.getContaByCliente(cliente.id)?.saldoCentavos ?: 0
                        if (boundClienteId == cliente.id) {
                            setPagamentoText(saldo.centavosParaTextoDecimal())
                            onUpdateValorTotal(cliente.id, saldo)
                        }
                    }
                }

                btnConfirmarPagamento.setOnClickListener {
                    onConfirmarOperacao(cliente.id)
                }

                btnCancelarPagamento.setOnClickListener {
                    clearPagamentoText()
                    onUpdateValorTotal(cliente.id, 0)
                    onCancelarOperacao(cliente.id)
                }
            }
        }

        private fun setupPagamentoWatcher() {
            binding.etValorPagamento.addTextChangedListener(
                object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        when (
                            val result =
                                pagamentoInputController.parseChange(
                                    boundClienteId = boundClienteId,
                                    isProgrammatic = isBindingPagamento,
                                    text = s?.toString().orEmpty(),
                                )
                        ) {
                            PagamentoInputController.Result.Ignorado -> Unit
                            is PagamentoInputController.Result.Valido -> {
                                binding.etValorPagamento.error = null
                                onUpdateValorTotal(result.clienteId, result.centavos)
                            }
                            is PagamentoInputController.Result.Erro -> {
                                binding.etValorPagamento.error = result.mensagem
                            }
                        }
                    }

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
                },
            )
        }

        private fun setupConfirmationButtons(cliente: Cliente) {
            binding.apply {
                btnConfirmarOperacao.setOnClickListener {
                    onConfirmarOperacao(cliente.id)
                    updateButtonStates(cliente)
                    resetContadores()
                }

                btnCancelarOperacao.setOnClickListener {
                    onCancelarOperacao(cliente.id)
                    updateButtonStates(cliente)
                    resetContadores()
                }
            }
        }

        private fun updateButtonStates(cliente: Cliente) {
            val state = getClienteState(cliente.id) ?: ClienteState(clienteId = cliente.id)
            val config = getConfiguracoes()
            val promocoesAtivadas = config?.promocoesAtivadas == true

            updatePromocaoActionVisibility(promocoesAtivadas)
            updateButtonSelections(state, promocoesAtivadas)
            updateLayoutVisibility(state, promocoesAtivadas)
            updateContadoresState(state)
            restorePagamentoText(state)
            config?.let { updatePromocoesInfo(state, it) }
        }

        private fun updatePromocaoActionVisibility(promocoesAtivadas: Boolean) {
            val visibility = if (promocoesAtivadas) View.VISIBLE else View.GONE
            binding.btnPromocao.visibility = visibility
            binding.btnPromocao.isEnabled = promocoesAtivadas
            binding.tvLabelPromocao.visibility = visibility
        }

        private fun updateButtonSelections(
            state: ClienteState,
            promocoesAtivadas: Boolean,
        ) {
            sincronizandoEstado = true
            try {
                sincronizarToggleModo(state, promocoesAtivadas)
                sincronizarToggleTipo(state)
            } finally {
                sincronizandoEstado = false
            }
            updateButtonStyles()
        }

        private fun sincronizarToggleModo(
            state: ClienteState,
            promocoesAtivadas: Boolean,
        ) {
            val modoChecado =
                when {
                    state.modoOperacao == ModoOperacao.VENDA -> R.id.btnVenda
                    promocoesAtivadas && state.modoOperacao == ModoOperacao.PROMOCAO -> R.id.btnPromocao
                    state.modoOperacao == ModoOperacao.PAGAMENTO -> R.id.btnPagamento
                    else -> null
                }
            if (modoChecado != null) {
                binding.toggleGroupModo.check(modoChecado)
            } else {
                binding.toggleGroupModo.clearChecked()
            }
        }

        private fun sincronizarToggleTipo(state: ClienteState) {
            val isVendaOrPromocao =
                state.modoOperacao == ModoOperacao.VENDA ||
                    state.modoOperacao == ModoOperacao.PROMOCAO
            val tipoChecado =
                when {
                    isVendaOrPromocao && state.tipoTransacao == TipoTransacao.A_VISTA -> R.id.btnAVista
                    isVendaOrPromocao && state.tipoTransacao == TipoTransacao.A_PRAZO -> R.id.btnAPrazo
                    else -> null
                }
            if (tipoChecado != null) {
                binding.toggleGroupTipo.check(tipoChecado)
            } else {
                binding.toggleGroupTipo.clearChecked()
            }
        }

        @Suppress("CyclomaticComplexMethod")
        private fun updateLayoutVisibility(
            state: ClienteState,
            promocoesAtivadas: Boolean,
        ) {
            val modoPromocaoAtivo = promocoesAtivadas && state.modoOperacao == ModoOperacao.PROMOCAO
            binding.apply {
                layoutVenda.visibility =
                    when {
                        state.modoOperacao == null -> View.GONE
                        state.modoOperacao == ModoOperacao.PAGAMENTO -> View.GONE
                        else -> View.VISIBLE
                    }

                layoutBotoesVenda.visibility =
                    when {
                        state.modoOperacao == ModoOperacao.VENDA ||
                            modoPromocaoAtivo -> View.VISIBLE
                        else -> View.GONE
                    }

                layoutVendaNormal.visibility =
                    when {
                        state.modoOperacao == ModoOperacao.VENDA &&
                            state.tipoTransacao != null -> View.VISIBLE
                        else -> View.GONE
                    }

                layoutVendaPromocao.visibility =
                    when {
                        modoPromocaoAtivo &&
                            state.tipoTransacao != null -> View.VISIBLE
                        else -> View.GONE
                    }

                layoutPagamento.visibility =
                    when {
                        state.modoOperacao == ModoOperacao.PAGAMENTO -> View.VISIBLE
                        else -> View.GONE
                    }

                layoutBotoesConfirmacao.visibility =
                    when {
                        (
                            state.modoOperacao == ModoOperacao.VENDA ||
                                modoPromocaoAtivo
                        ) &&
                            state.tipoTransacao != null -> View.VISIBLE
                        else -> View.GONE
                    }
            }
        }

        private fun updateContadoresState(state: ClienteState) {
            if (state.modoOperacao == ModoOperacao.PROMOCAO) {
                contadorPromo1Helper?.setQuantidade(state.quantidadePromo1)
                contadorPromo2Helper?.setQuantidade(state.quantidadePromo2)
                binding.etValorTotalPromocao.setText(state.valorTotalCentavos.centavosParaTextoDecimal())
            } else {
                contadorSalgadosHelper?.setQuantidade(state.quantidadeSalgados)
                contadorSucosHelper?.setQuantidade(state.quantidadeSucos)
                binding.etValorTotal.setText(state.valorTotalCentavos.centavosParaTextoDecimal())
            }
        }

        private fun updatePromocoesInfo(
            state: ClienteState,
            config: Configuracoes,
        ) {
            if (state.modoOperacao == ModoOperacao.PROMOCAO) {
                binding.apply {
                    contadorPromo1.root.visibility =
                        if (config.isPromocaoValida(1)) View.VISIBLE else View.GONE
                    contadorPromo2.root.visibility =
                        if (config.isPromocaoValida(2)) View.VISIBLE else View.GONE
                    etValorTotalPromocao.setText(state.valorTotalCentavos.centavosParaTextoDecimal())
                }
            }
        }

        fun updateValorTotal(payload: Payload.ValorTotalChanged) {
            binding.etValorTotal.setText(payload.novoValorCentavos.centavosParaTextoDecimal())
            binding.etValorTotalPromocao.setText(payload.novoValorCentavos.centavosParaTextoDecimal())
            val state = boundClienteId?.let(getClienteState)
            if (state?.modoOperacao == ModoOperacao.PAGAMENTO) {
                setPagamentoText(payload.novoValorCentavos.centavosParaTextoDecimal())
            }
        }

        private fun resetContadores() {
            contadorSalgadosHelper?.reset()
            contadorSucosHelper?.reset()
            contadorPromo1Helper?.reset()
            contadorPromo2Helper?.reset()

            binding.apply {
                etValorTotal.setText("0.00")
                etValorTotalPromocao.setText("0.00")
                clearPagamentoText()
            }
        }

        private fun restorePagamentoText(state: ClienteState) {
            if (state.modoOperacao == ModoOperacao.PAGAMENTO && state.valorTotalCentavos > 0) {
                setPagamentoText(state.valorTotalCentavos.centavosParaTextoDecimal())
            } else {
                clearPagamentoText()
            }
        }

        private fun setPagamentoText(text: String) {
            isBindingPagamento = true
            try {
                binding.etValorPagamento.setText(text)
            } finally {
                isBindingPagamento = false
            }
        }

        private fun clearPagamentoText() {
            isBindingPagamento = true
            try {
                binding.etValorPagamento.text?.clear()
                binding.etValorPagamento.error = null
            } finally {
                isBindingPagamento = false
            }
        }

        private fun updateButtonStyles() {
            binding.apply {
                listOf(btnVenda, btnPromocao, btnPagamento, btnAVista, btnAPrazo).forEach { button ->
                    updateButtonStyle(button)
                }
            }
        }

        private fun updateButtonStyle(button: MaterialButton) {
            val context = button.context
            val (backgroundColor, iconTint) =
                if (button.isChecked) {
                    Pair(R.color.button_background_selected, R.color.button_icon_selected)
                } else {
                    Pair(R.color.button_background_unselected, R.color.button_icon_unselected)
                }

            button.apply {
                setBackgroundColor(ContextCompat.getColor(context, backgroundColor))
                setIconTintResource(iconTint)
                elevation = 0f
                stateListAnimator = createStateListAnimator()
                ViewCompat.setStateDescription(
                    this,
                    context.getString(
                        if (isChecked) {
                            R.string.a11y_selecionado
                        } else {
                            R.string.a11y_nao_selecionado
                        },
                    ),
                )
            }
        }

        private fun setupButtonStateListeners() {
            binding.apply {
                listOf(btnVenda, btnPromocao, btnPagamento, btnAVista, btnAPrazo).forEach { button ->
                    button.stateListAnimator = createStateListAnimator()
                }
            }
        }

        private fun setupMenuOpcoes(cliente: Cliente) {
            binding.root.setOnLongClickListener {
                OpcoesClienteDialog
                    .newInstance(cliente.id, cliente.nome)
                    .show(fragmentManager, OpcoesClienteDialog.DIALOG_TAG)
                true
            }
        }

        @SuppressLint("ObjectAnimatorBinding")
        private fun createStateListAnimator(): StateListAnimator =
            StateListAnimator().apply {
                addState(
                    intArrayOf(android.R.attr.state_checked),
                    ObjectAnimator.ofFloat(null, "elevation", dpToPx(itemView.context, 8f)),
                )
                addState(
                    intArrayOf(),
                    ObjectAnimator.ofFloat(null, "elevation", 0f),
                )
            }
    }

    class ClienteDiffCallback : DiffUtil.ItemCallback<Cliente>() {
        override fun areItemsTheSame(
            oldItem: Cliente,
            newItem: Cliente,
        ): Boolean = oldItem.id == newItem.id

        override fun areContentsTheSame(
            oldItem: Cliente,
            newItem: Cliente,
        ): Boolean = oldItem == newItem
    }

    sealed class Payload {
        data class ValorTotalChanged(
            val novoValorCentavos: Long,
        ) : Payload()

        object SaldoChanged : Payload()

        object StateChanged : Payload()

        object ConfiguracoesChanged : Payload()
    }

    companion object {
        private fun dpToPx(
            context: Context,
            dp: Float,
        ): Float = dp * context.resources.displayMetrics.density

        private fun View.fixedImageSizePx(): Int =
            layoutParams.width.takeIf { it > 0 }
                ?: dpToPx(context, DEFAULT_CLIENTE_PHOTO_DP).toInt()

        private const val DEFAULT_CLIENTE_PHOTO_DP = 40f
    }
}
