package com.example.caderneta.ui.configuracoes

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.data.entity.Produto
import com.example.caderneta.databinding.ItemProdutoConfiguracoesBinding
import java.text.NumberFormat
import java.util.*

class ProdutosAdapter(
    private val onPrecoChanged: (Long, Double) -> Unit,
    private val onRemoveClick: (Long) -> Unit
) : ListAdapter<Produto, ProdutosAdapter.ViewHolder>(ProdutoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProdutoConfiguracoesBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemProdutoConfiguracoesBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(produto: Produto) {
            binding.tvNomeProduto.text = produto.nome
            binding.etPrecoProduto.setText(formatCurrency(produto.preco))

            binding.btnSalvarPreco.setOnClickListener {
                val novoPreco = binding.etPrecoProduto.text.toString().toDoubleOrNull()
                if (novoPreco != null) {
                    onPrecoChanged(produto.id, novoPreco)
                }
            }

            binding.btnRemoverProduto.setOnClickListener {
                onRemoveClick(produto.id)
            }
        }

        private fun formatCurrency(value: Double): String {
            return NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(value)
        }
    }

    class ProdutoDiffCallback : DiffUtil.ItemCallback<Produto>() {
        override fun areItemsTheSame(oldItem: Produto, newItem: Produto): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Produto, newItem: Produto): Boolean {
            return oldItem == newItem
        }
    }
}