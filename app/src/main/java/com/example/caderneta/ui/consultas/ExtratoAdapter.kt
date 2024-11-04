package com.example.caderneta.ui.consultas

import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.R
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.ItemExtratoBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class ExtratoAdapter(
    private val onItemClick: (Venda) -> Unit
) : ListAdapter<Venda, ExtratoAdapter.ViewHolder>(ExtratoDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExtratoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemExtratoBinding
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
                tvValor.text = numberFormat.format(venda.valor)
                tvValor.setTextColor(getTransactionColor(venda.transacao))

                // Handle quantities based on transaction type
                when (venda.transacao) {
                    "pagamento" -> {
                        tvPagamento.visibility = View.VISIBLE
                        tvTipoOperacao.visibility = View.GONE
                        tvQuantidadeSalgados.visibility = View.GONE
                        tvQuantidadeSucos.visibility = View.GONE
                    }
                    else -> {
                        tvPagamento.visibility = View.GONE

                        if (venda.isPromocao) {
                            tvTipoOperacao.apply {
                                visibility = View.VISIBLE
                                text = venda.promocaoDetalhes ?: "Promoção"
                            }
                        } else {
                            tvTipoOperacao.visibility = View.GONE
                        }

                        // Show quantities if they exist
                        if (venda.quantidadeSalgados > 0) {
                            tvQuantidadeSalgados.apply {
                                visibility = View.VISIBLE
                                text = formatQuantity(venda.quantidadeSalgados, "salgado")
                            }
                        } else {
                            tvQuantidadeSalgados.visibility = View.GONE
                        }

                        if (venda.quantidadeSucos > 0) {
                            tvQuantidadeSucos.apply {
                                visibility = View.VISIBLE
                                text = formatQuantity(venda.quantidadeSucos, "suco")
                            }
                        } else {
                            tvQuantidadeSucos.visibility = View.GONE
                        }
                    }
                }
            }
        }

        private fun formatQuantity(quantity: Int, item: String): String {
            return "$quantity ${item}${if (quantity > 1) "s" else ""}"
        }

        private fun getTransactionColor(transacao: String): Int {
            val colorResId = when (transacao) {
                "pagamento" -> R.color.green
                "a_vista" -> R.color.blue
                "a_prazo" -> R.color.red
                else -> R.color.on_surface
            }
            return ContextCompat.getColor(itemView.context, colorResId)
        }
    }

    class ExtratoDiffCallback : DiffUtil.ItemCallback<Venda>() {
        override fun areItemsTheSame(oldItem: Venda, newItem: Venda): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Venda, newItem: Venda): Boolean {
            return oldItem == newItem
        }
    }
}