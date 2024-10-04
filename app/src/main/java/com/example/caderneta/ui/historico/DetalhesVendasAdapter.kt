package com.example.caderneta.ui.historico

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.databinding.ItemDetalheVendaBinding
import java.text.NumberFormat
import java.util.*

class DetalhesVendasAdapter : ListAdapter<DetalheVenda, DetalhesVendasAdapter.ViewHolder>(DetalhesVendasDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetalheVendaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(private val binding: ItemDetalheVendaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(detalheVenda: DetalheVenda) {
            binding.tvNome.text = detalheVenda.nome
            binding.tvTotalVendas.text = formatCurrency(detalheVenda.totalVendas)
            binding.tvQuantidadeVendas.text = "Quantidade: ${detalheVenda.quantidadeVendas}"
        }

        private fun formatCurrency(value: Double): String {
            return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
        }
    }
}

class DetalhesVendasDiffCallback : DiffUtil.ItemCallback<DetalheVenda>() {
    override fun areItemsTheSame(oldItem: DetalheVenda, newItem: DetalheVenda): Boolean {
        return oldItem.nome == newItem.nome
    }

    override fun areContentsTheSame(oldItem: DetalheVenda, newItem: DetalheVenda): Boolean {
        return oldItem == newItem
    }
}

data class DetalheVenda(
    val nome: String,
    val totalVendas: Double,
    val quantidadeVendas: Int
)