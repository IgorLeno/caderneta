package com.example.caderneta.ui.consultas

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.ItemResultadoConsultaBinding
import com.example.caderneta.databinding.ItemExtratoBinding
import androidx.recyclerview.widget.LinearLayoutManager
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color

class ResultadosConsultaAdapter(
    private val onLocalClick: (Long) -> Unit,
    private val onClienteClick: (Long) -> Unit,
    private val onLimparExtratoClick: (Long) -> Unit
) : ListAdapter<ResultadoConsulta, RecyclerView.ViewHolder>(ResultadoConsultaDiffCallback()) {

    private var vendasPorCliente: Map<Long, List<Venda>> = emptyMap()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_LOCAL -> LocalViewHolder(
                ItemResultadoConsultaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            VIEW_TYPE_CLIENTE -> ClienteViewHolder(
                ItemResultadoConsultaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is LocalViewHolder -> holder.bind(item as ResultadoConsulta.Local)
            is ClienteViewHolder -> holder.bind(item as ResultadoConsulta.Cliente)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ResultadoConsulta.Local -> VIEW_TYPE_LOCAL
            is ResultadoConsulta.Cliente -> VIEW_TYPE_CLIENTE
        }
    }

    fun updateVendasPorCliente(vendasPorCliente: Map<Long, List<Venda>>) {
        this.vendasPorCliente = vendasPorCliente
        notifyDataSetChanged()
    }

    inner class LocalViewHolder(private val binding: ItemResultadoConsultaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(local: ResultadoConsulta.Local) {
            binding.tvNomeCliente.text = local.local.nome
            binding.tvNomePredio.visibility = View.GONE
            binding.tvValorDevido.visibility = View.GONE
            binding.layoutExtrato.visibility = View.GONE

            itemView.setOnClickListener { onLocalClick(local.local.id) }
        }
    }

    inner class ClienteViewHolder(private val binding: ItemResultadoConsultaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(cliente: ResultadoConsulta.Cliente) {
            binding.tvNomeCliente.text = cliente.cliente.nome
            binding.tvNomePredio.text = "Prédio: ${cliente.cliente.localId}" // Você pode buscar o nome do prédio aqui
            binding.tvValorDevido.text = "Valor devido: R$ ${String.format("%.2f", cliente.saldo)}"
            binding.tvValorDevido.setTextColor(
                if (cliente.saldo > 0) Color.RED else Color.GREEN
            )

            val vendas = vendasPorCliente[cliente.cliente.id] ?: emptyList()
            setupExtrato(cliente.cliente.id, vendas)

            itemView.setOnClickListener { onClienteClick(cliente.cliente.id) }
        }

        private fun setupExtrato(clienteId: Long, vendas: List<Venda>) {
            val extratoAdapter = ExtratoAdapter()
            binding.rvExtrato.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = extratoAdapter
            }
            extratoAdapter.submitList(vendas)

            binding.btnLimparExtrato.setOnClickListener { onLimparExtratoClick(clienteId) }

            binding.layoutExtrato.visibility = if (vendas.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    companion object {
        private const val VIEW_TYPE_LOCAL = 0
        private const val VIEW_TYPE_CLIENTE = 1
    }
}

class ResultadoConsultaDiffCallback : DiffUtil.ItemCallback<ResultadoConsulta>() {
    override fun areItemsTheSame(oldItem: ResultadoConsulta, newItem: ResultadoConsulta): Boolean {
        return when {
            oldItem is ResultadoConsulta.Local && newItem is ResultadoConsulta.Local ->
                oldItem.local.id == newItem.local.id
            oldItem is ResultadoConsulta.Cliente && newItem is ResultadoConsulta.Cliente ->
                oldItem.cliente.id == newItem.cliente.id
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ResultadoConsulta, newItem: ResultadoConsulta): Boolean {
        return oldItem == newItem
    }
}

sealed class ResultadoConsulta {
    data class Local(val local: com.example.caderneta.data.entity.Local) : ResultadoConsulta()
    data class Cliente(val cliente: com.example.caderneta.data.entity.Cliente, val saldo: Double) : ResultadoConsulta()
}

class ExtratoAdapter : ListAdapter<Venda, ExtratoAdapter.ExtratoViewHolder>(ExtratoDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExtratoViewHolder {
        val binding = ItemExtratoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ExtratoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ExtratoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ExtratoViewHolder(private val binding: ItemExtratoBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        fun bind(venda: Venda) {
            binding.tvData.text = dateFormat.format(venda.data)
            binding.tvQuantidade.text = "${venda.quantidadeSalgados} salgados, ${venda.quantidadeSucos} sucos"
            binding.tvValor.text = "R$ ${String.format("%.2f", venda.valor)}"
            binding.tvValor.setTextColor(
                if (venda.transacao == "a_vista") Color.GREEN else Color.RED
            )
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