package com.example.caderneta.ui.vendas

import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.content.Context
import android.content.res.ColorStateList
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
    private val onModoOperacaoSelected: (Cliente, ModoOperacao?) -> Unit,
    private val onTipoTransacaoSelected: (Cliente, TipoTransacao?) -> Unit,
    private val onUpdateQuantidadeSalgados: (Long, Int) -> Unit,
    private val onUpdateQuantidadeSucos: (Long, Int) -> Unit,
    private val onConfirmarOperacao: (Long) -> Unit,
    private val onCancelarOperacao: (Long) -> Unit,
    private val onPreviaPagamento: (Long, Double) -> Unit,
    private val onUpdateValorTotal: (Long, Double) -> Unit,
    private val observeSaldoAtualizado: ((suspend (Long) -> Unit) -> Unit)
) : ListAdapter<Cliente, ClientesAdapter.ClienteViewHolder>(ClienteDiffCallback()) {

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
        val cliente = getItem(position)
        holder.bind(cliente)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            val payload = payloads[0]
            when (payload) {
                is Payload.ValorTotalChanged -> holder.updateValorTotal(payload.novoValor)
                is Payload.SaldoChanged -> holder.updateSaldo()
                else -> super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    fun updateValorTotal(clienteId: Long, valor: Double) {
        val position = currentList.indexOfFirst { it.id == clienteId }
        if (position != -1) {
            notifyItemChanged(position, Payload.ValorTotalChanged(valor))
            Log.d("ClientesAdapter", "updateValorTotal chamado: clienteId=$clienteId, valor=$valor, position=$position")
        }
    }

    inner class ClienteViewHolder(private val binding: ItemClienteBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cliente: Cliente) {
            binding.tvNomeCliente.text = cliente.nome

            updateSaldo()

            setupMainButtons(cliente)
            setupVendaButtons(cliente)
            setupContadores(cliente)
            setupPagamentoLayout(cliente)
            setupConfirmationButtons(cliente)
            updateButtonStates(cliente)
            setupButtonStateListeners()
        }


        fun updateSaldo() {
            lifecycleScope.launch {
                val cliente = getItem(bindingAdapterPosition)
                val conta = contaRepository.getContaByCliente(cliente.id)
                val saldo = conta?.saldo ?: 0.0
                binding.tvValorDevido.text = "R$ %.2f".format(saldo)
                binding.tvValorDevido.setTextColor(
                    if (saldo > 0)
                        ContextCompat.getColor(itemView.context, R.color.delete_color)
                    else
                        ContextCompat.getColor(itemView.context, R.color.add_color)
                )
            }
        }

        private fun updateButtonStates(cliente: Cliente) {
            val clienteState = getClienteState(cliente.id) ?: VendasViewModel.ClienteState(clienteId = cliente.id)

            // Atualize o estado dos botões como antes
            binding.btnVenda.isSelected = clienteState.modoOperacao == ModoOperacao.VENDA
            binding.btnPromocao.isSelected = clienteState.modoOperacao == ModoOperacao.PROMOCAO
            binding.btnPagamento.isSelected = clienteState.modoOperacao == ModoOperacao.PAGAMENTO

            val isVendaOrPromocao = clienteState.modoOperacao == ModoOperacao.VENDA || clienteState.modoOperacao == ModoOperacao.PROMOCAO
            binding.btnAVista.isSelected = isVendaOrPromocao && clienteState.tipoTransacao == TipoTransacao.A_VISTA
            binding.btnAPrazo.isSelected = isVendaOrPromocao && clienteState.tipoTransacao == TipoTransacao.A_PRAZO

            updateButtonStyle(binding.btnVenda)
            updateButtonStyle(binding.btnPromocao)
            updateButtonStyle(binding.btnPagamento)
            updateButtonStyle(binding.btnAVista)
            updateButtonStyle(binding.btnAPrazo)

            updateLayoutVisibility(clienteState)

            // Redefina os contadores para zero na interface do usuário
            binding.contadorItem1.tvQuantidade.text = clienteState.quantidadeSalgados.toString()
            binding.contadorItem2.tvQuantidade.text = clienteState.quantidadeSucos.toString()

            // Atualize o valor total exibido
            binding.tvValorTotal.text = String.format("R$ %.2f", clienteState.valorTotal)
            binding.tvValorTotal.visibility = if (clienteState.valorTotal > 0) View.VISIBLE else View.GONE

            Log.d("ClientesAdapter", "Button states updated for cliente ${cliente.id}: venda=${binding.btnVenda.isSelected}, promocao=${binding.btnPromocao.isSelected}, pagamento=${binding.btnPagamento.isSelected}, aVista=${binding.btnAVista.isSelected}, aPrazo=${binding.btnAPrazo.isSelected}")
        }

        private fun updateLayoutVisibility(clienteState: VendasViewModel.ClienteState) {
            binding.layoutBotoesVenda.visibility = if (clienteState.modoOperacao == ModoOperacao.VENDA || clienteState.modoOperacao == ModoOperacao.PROMOCAO) View.VISIBLE else View.GONE
            binding.layoutVenda.visibility = if ((clienteState.modoOperacao == ModoOperacao.VENDA || clienteState.modoOperacao == ModoOperacao.PROMOCAO) && clienteState.tipoTransacao != null) View.VISIBLE else View.GONE
            binding.layoutPagamento.visibility = if (clienteState.modoOperacao == ModoOperacao.PAGAMENTO) View.VISIBLE else View.GONE

            val showConfirmationButtons = clienteState.modoOperacao != null &&
                    (clienteState.modoOperacao == ModoOperacao.PAGAMENTO ||
                            (clienteState.modoOperacao != ModoOperacao.PAGAMENTO && clienteState.tipoTransacao != null))

            binding.layoutBotoesConfirmacao.visibility = if (showConfirmationButtons) View.VISIBLE else View.GONE
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

            binding.btnVenda.stateListAnimator = stateListAnimator
            binding.btnPromocao.stateListAnimator = stateListAnimator
            binding.btnPagamento.stateListAnimator = stateListAnimator
            binding.btnAVista.stateListAnimator = stateListAnimator
            binding.btnAPrazo.stateListAnimator = stateListAnimator
        }

        private fun updateButtonStyle(button: MaterialButton) {
            val context = button.context
            if (button.isSelected) {
                button.setBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        R.color.button_background_selected
                    )
                )
                button.setIconTintResource(R.color.button_icon_selected)
                button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.button_background_selected))
            } else {
                button.setBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        R.color.button_background_unselected
                    )
                )
                button.setIconTintResource(R.color.button_icon_unselected)
                button.strokeColor = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.button_stroke))
            }
            button.elevation = 0f
            button.stateListAnimator = null
        }

        private fun setupMainButtons(cliente: Cliente) {
            binding.btnVenda.setOnClickListener {
                Log.d("ClientesAdapter", "Venda button clicked for cliente ${cliente.id}")
                val currentState = getClienteState(cliente.id)?.modoOperacao
                val newModoOperacao = if (currentState == ModoOperacao.VENDA) null else ModoOperacao.VENDA
                onModoOperacaoSelected(cliente, newModoOperacao)
                updateButtonStates(cliente)
            }

            binding.btnPromocao.setOnClickListener {
                Log.d("ClientesAdapter", "Promocao button clicked for cliente ${cliente.id}")
                val currentState = getClienteState(cliente.id)?.modoOperacao
                val newModoOperacao = if (currentState == ModoOperacao.PROMOCAO) null else ModoOperacao.PROMOCAO
                onModoOperacaoSelected(cliente, newModoOperacao)
                updateButtonStates(cliente)
            }

            binding.btnPagamento.setOnClickListener {
                Log.d("ClientesAdapter", "Pagamento button clicked for cliente ${cliente.id}")
                val currentState = getClienteState(cliente.id)?.modoOperacao
                val newModoOperacao = if (currentState == ModoOperacao.PAGAMENTO) null else ModoOperacao.PAGAMENTO
                onModoOperacaoSelected(cliente, newModoOperacao)
                updateButtonStates(cliente)
            }
        }

        private fun setupVendaButtons(cliente: Cliente) {
            binding.btnAVista.setOnClickListener {
                Log.d("ClientesAdapter", "A Vista button clicked for cliente ${cliente.id}")
                val clienteState = getClienteState(cliente.id) ?: VendasViewModel.ClienteState(clienteId = cliente.id)
                val newTipoTransacao = if (clienteState.tipoTransacao == TipoTransacao.A_VISTA) null else TipoTransacao.A_VISTA
                onTipoTransacaoSelected(cliente, newTipoTransacao)
                updateButtonStates(cliente)
            }

            binding.btnAPrazo.setOnClickListener {
                Log.d("ClientesAdapter", "A Prazo button clicked for cliente ${cliente.id}")
                val clienteState = getClienteState(cliente.id) ?: VendasViewModel.ClienteState(clienteId = cliente.id)
                val newTipoTransacao = if (clienteState.tipoTransacao == TipoTransacao.A_PRAZO) null else TipoTransacao.A_PRAZO
                onTipoTransacaoSelected(cliente, newTipoTransacao)
                updateButtonStates(cliente)
            }
        }

        private fun setupContadores(cliente: Cliente) {
            binding.contadorItem1.btnMais.setOnClickListener {
                val quantidadeAtual = binding.contadorItem1.tvQuantidade.text.toString().toInt()
                val novaQuantidade = quantidadeAtual + 1
                binding.contadorItem1.tvQuantidade.text = novaQuantidade.toString()
                onUpdateQuantidadeSalgados(cliente.id, novaQuantidade)
                updateValorTotalLocal(cliente.id)
                Log.d("ClientesAdapter", "Botão + de salgados clicado: clienteId=${cliente.id}, novaQuantidade=$novaQuantidade")
            }
            binding.contadorItem1.btnMenos.setOnClickListener {
                val quantidadeAtual = binding.contadorItem1.tvQuantidade.text.toString().toInt()
                if (quantidadeAtual > 0) {
                    val novaQuantidade = quantidadeAtual - 1
                    binding.contadorItem1.tvQuantidade.text = novaQuantidade.toString()
                    onUpdateQuantidadeSalgados(cliente.id, novaQuantidade)
                    updateValorTotalLocal(cliente.id)
                    Log.d("ClientesAdapter", "Botão - de salgados clicado: clienteId=${cliente.id}, novaQuantidade=$novaQuantidade")
                }
            }

            binding.contadorItem2.btnMais.setOnClickListener {
                val quantidadeAtual = binding.contadorItem2.tvQuantidade.text.toString().toInt()
                val novaQuantidade = quantidadeAtual + 1
                binding.contadorItem2.tvQuantidade.text = novaQuantidade.toString()
                onUpdateQuantidadeSucos(cliente.id, novaQuantidade)
                updateValorTotalLocal(cliente.id)
                Log.d("ClientesAdapter", "Quantidade de sucos atualizada: $novaQuantidade para cliente ${cliente.id}")
            }
            binding.contadorItem2.btnMenos.setOnClickListener {
                val quantidadeAtual = binding.contadorItem2.tvQuantidade.text.toString().toInt()
                if (quantidadeAtual > 0) {
                    val novaQuantidade = quantidadeAtual - 1
                    binding.contadorItem2.tvQuantidade.text = novaQuantidade.toString()
                    onUpdateQuantidadeSucos(cliente.id, novaQuantidade)
                    updateValorTotalLocal(cliente.id)
                    Log.d("ClientesAdapter", "Quantidade de sucos atualizada: $novaQuantidade para cliente ${cliente.id}")
                }
            }
        }

        private fun updateValorTotalLocal(clienteId: Long) {
            val clienteState = getClienteState(clienteId)
            clienteState?.let {
                updateValorTotal(it.valorTotal)
            }
        }

        private fun setupPagamentoLayout(cliente: Cliente) {
            binding.btnTudo.setOnClickListener {
                lifecycleScope.launch {
                    val conta = contaRepository.getContaByCliente(cliente.id)
                    val saldo = conta?.saldo ?: 0.0
                    binding.etValorPagamento.setText(saldo.toString())
                }
            }
            binding.btnPrevia.setOnClickListener {
                val valorPagamento = binding.etValorPagamento.text.toString().toDoubleOrNull() ?: 0.0
                onPreviaPagamento(cliente.id, valorPagamento)
            }
        }

        private fun setupConfirmationButtons(cliente: Cliente) {
            binding.btnConfirmarOperacao.setOnClickListener {
                onConfirmarOperacao(cliente.id)
                updateButtonStates(cliente)
            }
            binding.btnCancelarOperacao.setOnClickListener {
                onCancelarOperacao(cliente.id)
                updateButtonStates(cliente)
            }
        }

        fun updateValorTotal(valor: Double) {
            binding.tvValorTotal.text = String.format("R$ %.2f", valor)
            binding.tvValorTotal.visibility = if (valor > 0) View.VISIBLE else View.GONE
            Log.d("ClientesAdapter", "Valor total atualizado na UI: clienteId=${bindingAdapterPosition}, valor=$valor")
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

    companion object {
        private fun dpToPx(context: Context, dp: Float): Float {
            return dp * context.resources.displayMetrics.density
        }
    }

    sealed class Payload {
        data class ValorTotalChanged(val novoValor: Double) : Payload()
        object SaldoChanged : Payload()
    }
}
