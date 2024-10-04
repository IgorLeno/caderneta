package com.example.caderneta.ui.vendas

import android.animation.ObjectAnimator
import android.animation.StateListAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.R
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.ModoOperacao
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.databinding.ItemClienteBinding
import android.content.Context
import android.util.Log
import com.example.caderneta.viewmodel.VendasViewModel
import com.google.android.material.button.MaterialButton

class ClientesAdapter(
    private val onModoOperacaoSelected: (Cliente, ModoOperacao, TipoTransacao?) -> Unit,
    private val onUpdateQuantidadeSalgados: (Long, Int) -> Unit,
    private val onUpdateQuantidadeSucos: (Long, Int) -> Unit,
    private val onConfirmarOperacao: (Long) -> Unit,
    private val onCancelarOperacao: (Long) -> Unit,
    private val onPreviaPagamento: (Long, Double) -> Unit,
    private val onUpdateContadoresVisibility: (Long, Boolean) -> Unit
) : ListAdapter<Cliente, ClientesAdapter.ClienteViewHolder>(ClienteDiffCallback()) {

    private data class ClienteState(
        var modoOperacao: ModoOperacao? = null,
        var tipoTransacao: TipoTransacao? = null
    )

    private val clienteStates = mutableMapOf<Long, VendasViewModel.ClienteState>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClienteViewHolder {
        val binding = ItemClienteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ClienteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ClienteViewHolder, position: Int) {
        val cliente = getItem(position)
        holder.bind(cliente)
    }

    fun updateModoOperacao(clienteId: Long, modoOperacao: ModoOperacao?) {
        val clienteState = clienteStates.getOrPut(clienteId) { VendasViewModel.ClienteState() }
        clienteState.modoOperacao = modoOperacao
        clienteState.tipoTransacao = null
        notifyItemChanged(currentList.indexOfFirst { it.id == clienteId })
    }

    fun Float.dpToPx(context: Context): Float {
        return this * context.resources.displayMetrics.density
    }

    fun updateClienteStates(newStates: Map<Long, VendasViewModel.ClienteState>) {
        clienteStates.clear()
        clienteStates.putAll(newStates)
        notifyDataSetChanged()
    }

    inner class ClienteViewHolder(private val binding: ItemClienteBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cliente: Cliente) {
            binding.tvNomeCliente.text = cliente.nome
            binding.tvValorDevido.text = "R$ %.2f".format(cliente.valorDevido)
            binding.tvValorDevido.setTextColor(
                if (cliente.valorDevido > 0)
                    ContextCompat.getColor(itemView.context, R.color.delete_color)
                else
                    ContextCompat.getColor(itemView.context, R.color.add_color)
            )

            setupMainButtons(cliente)
            setupVendaButtons(cliente)
            setupContadores()
            setupPagamentoLayout(cliente)
            updateButtonStates(cliente)
            setupButtonStateListeners()
        }

        private fun updateButtonStates(cliente: Cliente) {
            val clienteState = clienteStates[cliente.id] ?: VendasViewModel.ClienteState()

            binding.btnVenda.isSelected = clienteState.modoOperacao == ModoOperacao.VENDA
            binding.btnPromocao.isSelected = clienteState.modoOperacao == ModoOperacao.PROMOCAO
            binding.btnPagamento.isSelected = clienteState.modoOperacao == ModoOperacao.PAGAMENTO

            binding.btnAVista.isSelected = clienteState.tipoTransacao == TipoTransacao.A_VISTA
            binding.btnAPrazo.isSelected = clienteState.tipoTransacao == TipoTransacao.A_PRAZO

            updateButtonStyle(binding.btnVenda)
            updateButtonStyle(binding.btnPromocao)
            updateButtonStyle(binding.btnPagamento)
            updateButtonStyle(binding.btnAVista)
            updateButtonStyle(binding.btnAPrazo)

            updateLayoutVisibility(clienteState)

            Log.d("ClientesAdapter", "Button states updated for cliente ${cliente.id}: venda=${binding.btnVenda.isSelected}, promocao=${binding.btnPromocao.isSelected}, pagamento=${binding.btnPagamento.isSelected}, aVista=${binding.btnAVista.isSelected}, aPrazo=${binding.btnAPrazo.isSelected}")
        }

        private fun updateLayoutVisibility(clienteState: VendasViewModel.ClienteState) {
            binding.layoutBotoesVenda.visibility = if (clienteState.modoOperacao == ModoOperacao.VENDA || clienteState.modoOperacao == ModoOperacao.PROMOCAO) View.VISIBLE else View.GONE
            binding.layoutVenda.visibility = if ((clienteState.modoOperacao == ModoOperacao.VENDA || clienteState.modoOperacao == ModoOperacao.PROMOCAO) && clienteState.tipoTransacao != null) View.VISIBLE else View.GONE
            binding.layoutPagamento.visibility = if (clienteState.modoOperacao == ModoOperacao.PAGAMENTO) View.VISIBLE else View.GONE
        }


        private fun setupButtonStateListeners() {
            val stateListAnimator = StateListAnimator().apply {
                addState(intArrayOf(android.R.attr.state_selected),
                    ObjectAnimator.ofFloat(itemView, "elevation", 8f.dpToPx(itemView.context))
                )
                addState(intArrayOf(),
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
                button.setBackgroundColor(ContextCompat.getColor(context, R.color.button_background_selected))
                button.setIconTintResource(R.color.button_icon_selected)
                button.setStrokeColorResource(R.color.button_background_selected)
            } else {
                button.setBackgroundColor(ContextCompat.getColor(context, R.color.button_background_unselected))
                button.setIconTintResource(R.color.button_icon_unselected)
                button.setStrokeColorResource(R.color.button_stroke)
            }
        }

        private fun setupMainButtons(cliente: Cliente) {
            binding.btnVenda.setOnClickListener {
                Log.d("ClientesAdapter", "Venda button clicked for cliente ${cliente.id}")
                val currentState = clienteStates[cliente.id]?.modoOperacao
                if (currentState == ModoOperacao.VENDA) {
                    updateClienteState(cliente.id, null, null)
                } else {
                    updateClienteState(cliente.id, ModoOperacao.VENDA, null)
                }
                clienteStates[cliente.id]?.modoOperacao?.let { it1 ->
                    onModoOperacaoSelected(cliente,
                        it1, null)
                }
                updateButtonStates(cliente)
            }

            binding.btnPromocao.setOnClickListener {
                Log.d("ClientesAdapter", "Promocao button clicked for cliente ${cliente.id}")
                val currentState = clienteStates[cliente.id]?.modoOperacao
                if (currentState == ModoOperacao.PROMOCAO) {
                    updateClienteState(cliente.id, null, null)
                } else {
                    updateClienteState(cliente.id, ModoOperacao.PROMOCAO, null)
                }
                clienteStates[cliente.id]?.modoOperacao?.let { it1 ->
                    onModoOperacaoSelected(cliente,
                        it1, null)
                }
                updateButtonStates(cliente)
            }

            binding.btnPagamento.setOnClickListener {
                Log.d("ClientesAdapter", "Pagamento button clicked for cliente ${cliente.id}")
                val currentState = clienteStates[cliente.id]?.modoOperacao
                if (currentState == ModoOperacao.PAGAMENTO) {
                    updateClienteState(cliente.id, null, null)
                } else {
                    updateClienteState(cliente.id, ModoOperacao.PAGAMENTO, null)
                }
                clienteStates[cliente.id]?.modoOperacao?.let { it1 ->
                    onModoOperacaoSelected(cliente,
                        it1, null)
                }
                updateButtonStates(cliente)
            }
        }

        private fun setupVendaButtons(cliente: Cliente) {
            binding.btnAVista.setOnClickListener {
                Log.d("ClientesAdapter", "A Vista button clicked for cliente ${cliente.id}")
                val clienteState = clienteStates.getOrPut(cliente.id) { VendasViewModel.ClienteState() }
                if (clienteState.tipoTransacao == TipoTransacao.A_VISTA) {
                    updateClienteState(cliente.id, clienteState.modoOperacao, null)
                } else {
                    updateClienteState(cliente.id, clienteState.modoOperacao, TipoTransacao.A_VISTA)
                }
                onModoOperacaoSelected(cliente, clienteState.modoOperacao!!, clienteState.tipoTransacao)
                updateButtonStates(cliente)
            }

            binding.btnAPrazo.setOnClickListener {
                Log.d("ClientesAdapter", "A Prazo button clicked for cliente ${cliente.id}")
                val clienteState = clienteStates.getOrPut(cliente.id) { VendasViewModel.ClienteState() }
                if (clienteState.tipoTransacao == TipoTransacao.A_PRAZO) {
                    updateClienteState(cliente.id, clienteState.modoOperacao, null)
                } else {
                    updateClienteState(cliente.id, clienteState.modoOperacao, TipoTransacao.A_PRAZO)
                }
                onModoOperacaoSelected(cliente, clienteState.modoOperacao!!, clienteState.tipoTransacao)
                updateButtonStates(cliente)
            }
        }


        private fun updateIconsForVendaType() {
            val clienteId = getItem(bindingAdapterPosition).id
            val clienteState = clienteStates.getOrPut(clienteId) { VendasViewModel.ClienteState() }
            if (clienteState.modoOperacao == ModoOperacao.PROMOCAO) {
                binding.icItem1.setImageResource(R.drawable.ic_promo_one)
                binding.icItem2.setImageResource(R.drawable.ic_promo_two)
            } else {
                binding.icItem1.setImageResource(R.drawable.ic_salgado)
                binding.icItem2.setImageResource(R.drawable.ic_suco)
            }
        }

        private fun setupContadores() {
            binding.contadorItem1.btnMais.setOnClickListener {
                val clienteId = getItem(bindingAdapterPosition).id
                val novaQuantidade = binding.contadorItem1.tvQuantidade.text.toString().toInt() + 1
                binding.contadorItem1.tvQuantidade.text = novaQuantidade.toString()
                onUpdateQuantidadeSalgados(clienteId, novaQuantidade)
            }
            binding.contadorItem1.btnMenos.setOnClickListener {
                val clienteId = getItem(bindingAdapterPosition).id
                val quantidadeAtual = binding.contadorItem1.tvQuantidade.text.toString().toInt()
                if (quantidadeAtual > 0) {
                    val novaQuantidade = quantidadeAtual - 1
                    binding.contadorItem1.tvQuantidade.text = novaQuantidade.toString()
                    onUpdateQuantidadeSalgados(clienteId, novaQuantidade)
                }
            }
            binding.contadorItem2.btnMais.setOnClickListener {
                val clienteId = getItem(bindingAdapterPosition).id
                val novaQuantidade = binding.contadorItem2.tvQuantidade.text.toString().toInt() + 1
                binding.contadorItem2.tvQuantidade.text = novaQuantidade.toString()
                onUpdateQuantidadeSucos(clienteId, novaQuantidade)
            }
            binding.contadorItem2.btnMenos.setOnClickListener {
                val clienteId = getItem(bindingAdapterPosition).id
                val quantidadeAtual = binding.contadorItem2.tvQuantidade.text.toString().toInt()
                if (quantidadeAtual > 0) {
                    val novaQuantidade = quantidadeAtual - 1
                    binding.contadorItem2.tvQuantidade.text = novaQuantidade.toString()
                    onUpdateQuantidadeSucos(clienteId, novaQuantidade)
                }
            }
        }


        private fun setupPagamentoLayout(cliente: Cliente) {
            binding.btnTudo.setOnClickListener {
                binding.etValorPagamento.setText(cliente.valorDevido.toString())
            }
            binding.btnPrevia.setOnClickListener {
                val valorPagamento = binding.etValorPagamento.text.toString().toDoubleOrNull() ?: 0.0
                onPreviaPagamento(cliente.id, valorPagamento)
            }
            binding.btnConfirmarPagamento.setOnClickListener {
                onConfirmarOperacao(cliente.id)
                resetSelection()
            }
            binding.btnCancelarPagamento.setOnClickListener {
                onCancelarOperacao(cliente.id)
                resetSelection()
            }
        }

        fun showVendaLayout(show: Boolean) {
            binding.layoutBotoesVenda.visibility = if (show) View.VISIBLE else View.GONE
            binding.layoutVenda.visibility = View.GONE // Inicialmente oculto
        }

        fun updateContadoresVisibility(visible: Boolean) {
            binding.layoutVenda.visibility = if (visible) View.VISIBLE else View.GONE
        }

        fun showPagamentoLayout(show: Boolean) {
            binding.layoutPagamento.visibility = if (show) View.VISIBLE else View.GONE
            binding.layoutBotoesVenda.visibility = View.GONE
            binding.layoutVenda.visibility = View.GONE
        }

        fun updateQuantidadeSalgados(quantidade: Int) {
            binding.contadorItem1.tvQuantidade.text = quantidade.toString()
        }

        fun updateQuantidadeSucos(quantidade: Int) {
            binding.contadorItem2.tvQuantidade.text = quantidade.toString()
        }

        fun updateValorTotal(valor: Double) {
            binding.tvValorTotal.text = String.format("R$ %.2f", valor)
        }

        fun updateModoOperacao(modoOperacao: ModoOperacao?) {
            val clienteId = getItem(bindingAdapterPosition).id
            updateClienteState(clienteId, modoOperacao, null)
            updateIconsForVendaType()
            updateButtonStates(getItem(bindingAdapterPosition))
        }


        private fun updateClienteState(clienteId: Long, modoOperacao: ModoOperacao?, tipoTransacao: TipoTransacao?) {
            val clienteState = clienteStates.getOrPut(clienteId) { VendasViewModel.ClienteState() }
            clienteState.modoOperacao = modoOperacao
            clienteState.tipoTransacao = tipoTransacao

            if (modoOperacao == null) {
                clienteState.tipoTransacao = null
            }

            if (tipoTransacao == null) {
                clienteState.quantidadeSalgados = 0
                clienteState.quantidadeSucos = 0
            }
        }


        fun updateValorPagamento(valor: Double) {
            binding.etValorPagamento.setText(String.format("%.2f", valor))
        }

        private fun resetSelection() {
            val clienteId = getItem(bindingAdapterPosition).id
            clienteStates.remove(clienteId)
            updateButtonStates(getItem(bindingAdapterPosition))
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
}