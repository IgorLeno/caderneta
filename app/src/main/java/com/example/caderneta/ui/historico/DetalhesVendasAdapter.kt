package com.example.caderneta.ui.historico

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.databinding.ItemDetalheVendaBinding
import com.example.caderneta.util.centavosParaReais

class DetalhesVendasAdapter :
    ListAdapter<DetalheVenda, DetalhesVendasAdapter.ViewHolder>(DetalhesVendasDiffCallback()) {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding = ItemDetalheVendaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(getItem(position))
    }

    class ViewHolder(
        private val binding: ItemDetalheVendaBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(detalheVenda: DetalheVenda) {
            binding.tvNome.text = detalheVenda.nome
            binding.tvTotalVendas.text = detalheVenda.totalVendasCentavos.centavosParaReais()
            binding.tvQuantidadeVendas.text = "Quantidade: ${detalheVenda.quantidadeVendas}"
        }
    }
}

class DetalhesVendasDiffCallback : DiffUtil.ItemCallback<DetalheVenda>() {
    override fun areItemsTheSame(
        oldItem: DetalheVenda,
        newItem: DetalheVenda,
    ): Boolean = oldItem.nome == newItem.nome

    override fun areContentsTheSame(
        oldItem: DetalheVenda,
        newItem: DetalheVenda,
    ): Boolean = oldItem == newItem
}

data class DetalheVenda(
    val nome: String,
    val totalVendasCentavos: Long,
    val quantidadeVendas: Int,
)
