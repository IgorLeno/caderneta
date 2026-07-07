package com.example.caderneta.ui.consultas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.R
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.ItemExtratoBinding
import com.example.caderneta.util.centavosParaReais
import java.text.SimpleDateFormat
import java.util.Locale

class ExtratoAdapter(
    private val onItemClick: (Venda) -> Unit,
) : ListAdapter<Venda, ExtratoAdapter.ViewHolder>(ExtratoDiffCallback()) {
    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding =
            ItemExtratoBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemExtratoBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(venda: Venda) {
            binding.apply {
                // Set date
                tvData.text = dateFormat.format(venda.data)

                // Set value and its color
                tvValor.text = venda.valorCentavos.centavosParaReais()
                tvValor.setTextColor(getTransactionColor(venda.transacao))

                // Reset visibilities
                tvPagamento.visibility = View.GONE
                tvTipoOperacao.visibility = View.GONE
                tvQuantidadeSalgados.visibility = View.GONE
                tvQuantidadeSucos.visibility = View.GONE

                when (venda.transacao) {
                    TransacaoVenda.PAGAMENTO -> {
                        tvPagamento.visibility = View.VISIBLE
                    }
                    else -> {
                        // Handle promotion details
                        if (venda.isPromocao) {
                            tvTipoOperacao.apply {
                                visibility = View.VISIBLE
                                text = venda.promocaoDetalhes ?: "Promoção"
                            }
                        }

                        // Always show quantities if they exist, regardless of promotion status
                        if (venda.quantidadeSalgados > 0) {
                            tvQuantidadeSalgados.apply {
                                visibility = View.VISIBLE
                                text = formatQuantity(venda.quantidadeSalgados, "salgado")
                            }
                        }

                        if (venda.quantidadeSucos > 0) {
                            tvQuantidadeSucos.apply {
                                visibility = View.VISIBLE
                                text = formatQuantity(venda.quantidadeSucos, "suco")
                            }
                        }
                    }
                }
            }
        }

        private fun formatQuantity(
            quantity: Int,
            item: String,
        ): String = "$quantity ${item}${if (quantity > 1) "s" else ""}"

        private fun getTransactionColor(transacao: TransacaoVenda): Int {
            val colorResId =
                when (transacao) {
                    TransacaoVenda.PAGAMENTO -> R.color.green
                    TransacaoVenda.A_VISTA -> R.color.blue
                    TransacaoVenda.A_PRAZO -> R.color.red
                }
            return ContextCompat.getColor(itemView.context, colorResId)
        }
    }
}

class ExtratoDiffCallback : DiffUtil.ItemCallback<Venda>() {
    override fun areItemsTheSame(
        oldItem: Venda,
        newItem: Venda,
    ): Boolean = oldItem.id == newItem.id

    override fun areContentsTheSame(
        oldItem: Venda,
        newItem: Venda,
    ): Boolean = oldItem == newItem
}
