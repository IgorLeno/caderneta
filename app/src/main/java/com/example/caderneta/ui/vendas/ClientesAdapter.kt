package com.example.caderneta.ui.vendas

import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.R
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.databinding.ItemClienteBinding
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.viewmodel.VendasViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch

class ClientesAdapter(
    private val contaRepository: ContaRepository,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val getClienteState: (Long) -> VendasViewModel.ClienteState?,
    private val getConfiguracoes: () -> Configuracoes?,
    private val onModoOperacaoSelected: (Cliente, ModoOperacao?) -> Unit,
    private val onTipoTransacaoSelected: (Cliente, TipoTransacao?) -> Unit,
    private val onQuantidadeChanged: (Long, TipoQuantidade, Int) -> Unit,
    private val onConfirmarOperacao: (Long) -> Unit,
    private val onCancelarOperacao: (Long) -> Unit,
    private val onUpdateValorTotal: (Long, Double) -> Unit,
    private val observeSaldoAtualizado: ((suspend (Long) -> Unit) -> Unit)
) : ListAdapter<Cliente, ClientesAdapter.ClienteViewHolder>(ClienteDiffCallback()) {

    enum class TipoQuantidade {
        SALGADO, SUCO, PROMO1, PROMO2
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val binding = ItemClienteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClienteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            when (payloads[0]) {
                is Payload.ValorTotalChanged -> holder.updateValorTotal(payloads[0] as Payload.ValorTotalChanged)
                is Payload.SaldoChanged -> holder.updateSaldo()
                else -> super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ClienteViewHolder(private val binding: ItemClienteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cliente: Cliente) {
            setupBasicInfo(cliente)
            setupMainButtons(cliente)
            setupVendaButtons(cliente)
            setupContadores(cliente)
            setupPagamentoLayout(cliente)
            setupConfirmationButtons(cliente)
            updateButtonStates(cliente)
            setupButtonStateListeners()
        }

        private fun setupBasicInfo(cliente: Cliente) {
            binding.tvNomeCliente.text = cliente.nome
            updateSaldo()
        }

        fun updateSaldo() {
            lifecycleScope.launch {
                val cliente = getItem(bindingAdapterPosition)
                val saldo = contaRepository.getContaByCliente(cliente.id)?.saldo ?: 0.0
                binding.tvValorDevido.text = "R$ %.2f".format(saldo)
                binding.tvValorDevido.setTextColor(
                    ContextCompat.getColor(
                        itemView.context,
                        if (saldo > 0) R.color.delete_color else R.color.add_color
                    )
                )
            }
        }

        private fun setupMainButtons(cliente: Cliente) {
            binding.apply {
                btnVenda.setOnClickListener {
                    val currentState = getClienteState(cliente.id)?.modoOperacao
                    val newModoOperacao = if (currentState == ModoOperacao.VENDA) null else ModoOperacao.VENDA
                    onModoOperacaoSelected(cliente, newModoOperacao)
                    updateButtonStates(cliente)
                }

                btnPromocao.setOnClickListener {
                    val currentState = getClienteState(cliente.id)?.modoOperacao
                    val newModoOperacao = if (currentState == ModoOperacao.PROMOCAO) null else ModoOperacao.PROMOCAO
                    onModoOperacaoSelected(cliente, newModoOperacao)
                    updateButtonStates(cliente)
                }

                btnPagamento.setOnClickListener {
                    val currentState = getClienteState(cliente.id)?.modoOperacao
                    val newModoOperacao = if (currentState == ModoOperacao.PAGAMENTO) null else ModoOperacao.PAGAMENTO
                    onModoOperacaoSelected(cliente, newModoOperacao)
                    updateButtonStates(cliente)
                }
            }
        }

        private fun setupVendaButtons(cliente: Cliente) {
            binding.apply {
                btnAVista.setOnClickListener {
                    val newTipoTransacao = if (getClienteState(cliente.id)?.tipoTransacao == TipoTransacao.A_VISTA)
                        null else TipoTransacao.A_VISTA
                    onTipoTransacaoSelected(cliente, newTipoTransacao)
                    updateButtonStates(cliente)
                }

                btnAPrazo.setOnClickListener {
                    val newTipoTransacao = if (getClienteState(cliente.id)?.tipoTransacao == TipoTransacao.A_PRAZO)
                        null else TipoTransacao.A_PRAZO
                    onTipoTransacaoSelected(cliente, newTipoTransacao)
                    updateButtonStates(cliente)
                }
            }
        }

        private fun setupContadores(cliente: Cliente) {
            binding.apply {
                // Setup contadores modo normal
                contadorSalgados.apply {
                    btnMais.setOnClickListener {
                        atualizarQuantidade(cliente.id, TipoQuantidade.SALGADO, 1)
                    }
                    btnMenos.setOnClickListener {
                        atualizarQuantidade(cliente.id, TipoQuantidade.SALGADO, -1)
                    }
                    // Garantir visibilidade inicial
                    root.visibility = View.VISIBLE
                }

                contadorSucos.apply {
                    btnMais.setOnClickListener {
                        atualizarQuantidade(cliente.id, TipoQuantidade.SUCO, 1)
                    }
                    btnMenos.setOnClickListener {
                        atualizarQuantidade(cliente.id, TipoQuantidade.SUCO, -1)
                    }
                    // Garantir visibilidade inicial
                    root.visibility = View.VISIBLE
                }

                // Setup contadores modo promoção
                contadorPromo1.apply {
                    btnMais.setOnClickListener {
                        atualizarQuantidade(cliente.id, TipoQuantidade.PROMO1, 1)
                    }
                    btnMenos.setOnClickListener {
                        atualizarQuantidade(cliente.id, TipoQuantidade.PROMO1, -1)
                    }
                    // Garantir visibilidade inicial
                    root.visibility = View.VISIBLE
                }

                contadorPromo2.apply {
                    btnMais.setOnClickListener {
                        atualizarQuantidade(cliente.id, TipoQuantidade.PROMO2, 1)
                    }
                    btnMenos.setOnClickListener {
                        atualizarQuantidade(cliente.id, TipoQuantidade.PROMO2, -1)
                    }
                    // Garantir visibilidade inicial
                    root.visibility = View.VISIBLE
                }
            }
        }


        private fun setupContadorNormal(cliente: Cliente) {
            binding.contadorSalgados.apply {
                btnMais.setOnClickListener {
                    atualizarQuantidade(cliente.id, TipoQuantidade.SALGADO, 1)
                }
                btnMenos.setOnClickListener {
                    atualizarQuantidade(cliente.id, TipoQuantidade.SALGADO, -1)
                }
            }

            binding.contadorSucos.apply {
                btnMais.setOnClickListener {
                    atualizarQuantidade(cliente.id, TipoQuantidade.SUCO, 1)
                }
                btnMenos.setOnClickListener {
                    atualizarQuantidade(cliente.id, TipoQuantidade.SUCO, -1)
                }
            }
        }

        private fun setupContadorPromocao(cliente: Cliente) {
            binding.contadorPromo1.apply {
                btnMais.setOnClickListener {
                    atualizarQuantidade(cliente.id, TipoQuantidade.PROMO1, 1)
                }
                btnMenos.setOnClickListener {
                    atualizarQuantidade(cliente.id, TipoQuantidade.PROMO1, -1)
                }
            }

            binding.contadorPromo2.apply {
                btnMais.setOnClickListener {
                    atualizarQuantidade(cliente.id, TipoQuantidade.PROMO2, 1)
                }
                btnMenos.setOnClickListener {
                    atualizarQuantidade(cliente.id, TipoQuantidade.PROMO2, -1)
                }
            }
        }



        private fun atualizarQuantidade(clienteId: Long, tipo: TipoQuantidade, delta: Int) {
            val state = getClienteState(clienteId) ?: return
            val novaQuantidade = when (tipo) {
                TipoQuantidade.SALGADO -> (state.quantidadeSalgados + delta).coerceAtLeast(0)
                TipoQuantidade.SUCO -> (state.quantidadeSucos + delta).coerceAtLeast(0)
                TipoQuantidade.PROMO1 -> (state.quantidadePromo1 + delta).coerceAtLeast(0)
                TipoQuantidade.PROMO2 -> (state.quantidadePromo2 + delta).coerceAtLeast(0)
            }
            onQuantidadeChanged(clienteId, tipo, novaQuantidade)
        }

        private fun setupPagamentoLayout(cliente: Cliente) {
            binding.apply {
                btnTudo.setOnClickListener {
                    lifecycleScope.launch {
                        val saldo = contaRepository.getContaByCliente(cliente.id)?.saldo ?: 0.0
                        etValorPagamento.setText(saldo.toString())
                        onUpdateValorTotal(cliente.id, saldo)
                    }
                }

                etValorPagamento.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        onUpdateValorTotal(cliente.id, s.toString().toDoubleOrNull() ?: 0.0)
                    }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })

                btnConfirmarPagamento.setOnClickListener {
                    onConfirmarOperacao(cliente.id)
                }

                btnCancelarPagamento.setOnClickListener {
                    etValorPagamento.text?.clear()
                    onUpdateValorTotal(cliente.id, 0.0)
                    onCancelarOperacao(cliente.id)
                }
            }
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
            val state = getClienteState(cliente.id) ?: return
            val config = getConfiguracoes() ?: return

            updateButtonSelections(state)
            updateLayoutVisibility(state)

            // Garantir que os contadores estão visíveis e com valores corretos
            binding.apply {
                // Atualizar valores dos contadores
                contadorSalgados.tvQuantidade.text = state.quantidadeSalgados.toString()
                contadorSucos.tvQuantidade.text = state.quantidadeSucos.toString()
                contadorPromo1.tvQuantidade.text = state.quantidadePromo1.toString()
                contadorPromo2.tvQuantidade.text = state.quantidadePromo2.toString()

                // Atualizar valores totais
                etValorTotal.setText(String.format("%.2f", state.valorTotal))
                etValorTotalPromocao.setText(String.format("%.2f", state.valorTotal))
            }

            updateContadoresState(state)
            updatePromocoesInfo(state, config)
        }

        private fun updateButtonSelections(state: VendasViewModel.ClienteState) {
            binding.apply {
                btnVenda.isSelected = state.modoOperacao == ModoOperacao.VENDA
                btnPromocao.isSelected = state.modoOperacao == ModoOperacao.PROMOCAO
                btnPagamento.isSelected = state.modoOperacao == ModoOperacao.PAGAMENTO

                val isVendaOrPromocao = state.modoOperacao == ModoOperacao.VENDA ||
                        state.modoOperacao == ModoOperacao.PROMOCAO
                btnAVista.isSelected = isVendaOrPromocao && state.tipoTransacao == TipoTransacao.A_VISTA
                btnAPrazo.isSelected = isVendaOrPromocao && state.tipoTransacao == TipoTransacao.A_PRAZO

                updateButtonStyles()
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
            var (backgroundColor, iconTint, strokeColor) = if (button.isSelected) {
                Triple(
                    R.color.button_background_selected,
                    R.color.button_icon_selected,
                    R.color.button_background_selected
                )
            } else {
                Triple(
                    R.color.button_background_unselected,
                    R.color.button_icon_unselected,
                    R.color.button_stroke
                )
            }

            button.apply {
                setBackgroundColor(ContextCompat.getColor(context, backgroundColor))
                setIconTintResource(iconTint)
                elevation = 0f
                stateListAnimator = null
            }
        }

        private fun updateLayoutVisibility(state: VendasViewModel.ClienteState) {
            binding.apply {
                // Layout principal de venda (container para ambos os modos)
                layoutVenda.visibility = when {
                    state.modoOperacao == null -> View.GONE
                    state.modoOperacao == ModoOperacao.PAGAMENTO -> View.GONE
                    else -> View.VISIBLE
                }

                // Layout de botões de tipo de transação
                layoutBotoesVenda.visibility = when {
                    state.modoOperacao == ModoOperacao.VENDA ||
                            state.modoOperacao == ModoOperacao.PROMOCAO -> View.VISIBLE
                    else -> View.GONE
                }

                // Layout específico de venda normal
                layoutVendaNormal.visibility = when {
                    state.modoOperacao == ModoOperacao.VENDA &&
                            state.tipoTransacao != null -> View.VISIBLE
                    else -> View.GONE
                }

                // Layout específico de promoção
                layoutVendaPromocao.visibility = when {
                    state.modoOperacao == ModoOperacao.PROMOCAO &&
                            state.tipoTransacao != null -> View.VISIBLE
                    else -> View.GONE
                }

                // Layout de pagamento
                layoutPagamento.visibility = when {
                    state.modoOperacao == ModoOperacao.PAGAMENTO -> View.VISIBLE
                    else -> View.GONE
                }

                // Botões de confirmação
                layoutBotoesConfirmacao.visibility = when {
                    (state.modoOperacao == ModoOperacao.VENDA ||
                            state.modoOperacao == ModoOperacao.PROMOCAO) &&
                            state.tipoTransacao != null -> View.VISIBLE
                    else -> View.GONE
                }
            }
        }

        private fun updateContadoresState(state: VendasViewModel.ClienteState) {
            if (state.modoOperacao == ModoOperacao.PROMOCAO) {
                binding.contadorPromo1.tvQuantidade.text = state.quantidadePromo1.toString()
                binding.contadorPromo2.tvQuantidade.text = state.quantidadePromo2.toString()
                binding.etValorTotalPromocao.setText(String.format("%.2f", state.valorTotal))
            } else {
                binding.contadorSalgados.tvQuantidade.text = state.quantidadeSalgados.toString()
                binding.contadorSucos.tvQuantidade.text = state.quantidadeSucos.toString()
                binding.etValorTotal.setText(String.format("%.2f", state.valorTotal))
            }
        }


        private fun updatePromocoesInfo(state: VendasViewModel.ClienteState, config: Configuracoes) {
            if (state.modoOperacao == ModoOperacao.PROMOCAO) {
                // Atualizar texto e visibilidade de Promo 1
                if (config.isPromocaoValida(1)) {
                    binding.contadorPromo1.root.visibility = View.VISIBLE
                    binding.icPromo1.setImageResource(R.drawable.ic_promotion)
                } else {
                    binding.contadorPromo1.root.visibility = View.GONE
                }

                // Atualizar texto e visibilidade de Promo 2
                if (config.isPromocaoValida(2)) {
                    binding.contadorPromo2.root.visibility = View.VISIBLE
                    binding.icPromo2.setImageResource(R.drawable.ic_promotion)
                } else {
                    binding.contadorPromo2.root.visibility = View.GONE
                }

                // Atualizar valor total da promoção
                binding.etValorTotalPromocao.setText(String.format("%.2f", state.valorTotal))
            }
        }

        fun updateValorTotal(payload: Payload.ValorTotalChanged) {
            binding.etValorTotal.setText(String.format("%.2f", payload.novoValor))
        }

        private fun resetContadores() {
            binding.apply {
                contadorSalgados.tvQuantidade.text = "0"
                contadorSucos.tvQuantidade.text = "0"
                contadorPromo1.tvQuantidade.text = "0"
                contadorPromo2.tvQuantidade.text = "0"
                etValorTotal.setText("0.00")
                etValorTotalPromocao.setText("0.00")
                etValorPagamento.text?.clear()
            }
        }

        private fun setupButtonStateListeners() {
            val stateListAnimator = StateListAnimator().apply {
                addState(
                    intArrayOf(android.R.attr.state_selected),
                    ObjectAnimator.ofFloat(itemView, "elevation", dpToPx(itemView.context, 8f))
                )
                addState(
                    intArrayOf(),
                    ObjectAnimator.ofFloat(itemView, "elevation", 0f)
                )
            }

            binding.apply {
                btnVenda.stateListAnimator = stateListAnimator
                btnPromocao.stateListAnimator = stateListAnimator
                btnPagamento.stateListAnimator = stateListAnimator
                btnAVista.stateListAnimator = stateListAnimator
                btnAPrazo.stateListAnimator = stateListAnimator
            }
        }
    }

    class ClienteDiffCallback : DiffUtil.ItemCallback<Cliente>() {
        override fun areItemsTheSame(oldItem: Cliente, newItem: Cliente): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Cliente, newItem: Cliente): Boolean {
            return oldItem == newItem
        }
    }

    sealed class Payload {
        data class ValorTotalChanged(val novoValor: Double) : Payload()
        object SaldoChanged : Payload()
    }

    companion object {
        private fun dpToPx(context: Context, dp: Float): Float {
            return dp * context.resources.displayMetrics.density
        }
    }
}