package com.example.caderneta.ui.consultas

import android.os.Build
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.R
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.ItemExtratoBinding
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class ExtratoAdapter : ListAdapter<Venda, ExtratoAdapter.ViewHolder>(ExtratoDiffCallback()) {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExtratoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemExtratoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @RequiresApi(Build.VERSION_CODES.M)
        fun bind(venda: Venda) {
            binding.apply {
                tvData.text = dateFormat.format(venda.data)
                tvQuantidade.text = formatarQuantidades(venda)
                tvValor.text = formatarValor(venda)
                tvValor.setTextColor(getCorTransacao(venda.transacao))
            }
        }

        private fun formatarQuantidades(venda: Venda): String {
            return when (venda.transacao) {
                "pagamento" -> "Pagamento"
                else -> buildString {
                    if (venda.quantidadeSalgados > 0) {
                        append("${venda.quantidadeSalgados} salgado")
                        if (venda.quantidadeSalgados > 1) append("s")
                    }
                    if (venda.quantidadeSucos > 0) {
                        if (isNotEmpty()) append(", ")
                        append("${venda.quantidadeSucos} suco")
                        if (venda.quantidadeSucos > 1) append("s")
                    }
                }
            }
        }

        private fun formatarValor(venda: Venda): String {
            return numberFormat.format(venda.valor)
        }

        @RequiresApi(Build.VERSION_CODES.M)
        private fun getCorTransacao(transacao: String): Int {
            return itemView.context.getColor(
                when (transacao) {
                    "pagamento" -> R.color.green
                    "a_vista" -> R.color.blue
                    "a_prazo" -> R.color.red
                    else -> R.color.on_surface
                }
            )
        }
    }

    private class ExtratoDiffCallback : DiffUtil.ItemCallback<Venda>() {
        override fun areItemsTheSame(oldItem: Venda, newItem: Venda): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Venda, newItem: Venda): Boolean {
            return oldItem == newItem
        }
    }
}