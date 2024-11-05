package com.example.caderneta.ui.consultas

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.ItemResultadoConsultaBinding
import java.text.NumberFormat
import java.util.Locale
import android.view.animation.AccelerateDecelerateInterpolator
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.caderneta.R
import com.example.caderneta.repository.LocalRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ResultadosConsultaAdapter(
    private val onLocalClick: (Long) -> Unit,
    private val onClienteClick: (Long) -> Unit,
    private val onExtratoItemClick: (Venda, Cliente) -> Unit,
    private val localRepository: LocalRepository,
    private val coroutineScope: CoroutineScope
) : ListAdapter<ResultadoConsulta, RecyclerView.ViewHolder>(ResultadoConsultaDiffCallback()) {

    private var vendasPorCliente: Map<Long, List<Venda>> = emptyMap()
    private val expandedClientes = mutableSetOf<Long>()
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ResultadoConsulta.Local -> VIEW_TYPE_LOCAL
            is ResultadoConsulta.Cliente -> VIEW_TYPE_CLIENTE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val binding = ItemResultadoConsultaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return when (viewType) {
            VIEW_TYPE_LOCAL -> LocalViewHolder(binding)
            VIEW_TYPE_CLIENTE -> ClienteViewHolder(binding)
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ResultadoConsulta.Local -> (holder as LocalViewHolder).bind(item)
            is ResultadoConsulta.Cliente -> (holder as ClienteViewHolder).bind(item)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        if (payloads.isNotEmpty()) {
            val payload = payloads.first()
            when {
                payload is SaldoPayload && holder is ClienteViewHolder -> {
                    holder.atualizarSaldo(payload.novoSaldo)
                }
                else -> super.onBindViewHolder(holder, position, payloads)
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    inner class ClienteViewHolder(private val binding: ItemResultadoConsultaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var extratoAdapter: ExtratoAdapter? = null

        fun bind(resultado: ResultadoConsulta.Cliente) {
            val cliente = resultado.cliente
            Log.d("SaldoDebug", "Binding cliente ${cliente.id} - Saldo: ${resultado.saldo}")

            binding.apply {
                tvNomeCliente.text = cliente.nome
                tvTelefone.text = cliente.telefone?.takeIf { it.isNotBlank() } ?: "Não informado"
                atualizarSaldo(resultado.saldo)
                construirHierarquiaLocal(cliente)
                setupExtratoAdapter(cliente)
                setupClickListeners(cliente)
                atualizarExpandState(cliente)
            }
        }

        fun atualizarSaldo(novoSaldo: Double) {
            binding.tvValorDevido.apply {
                val saldoFormatado = numberFormat.format(novoSaldo)
                text = saldoFormatado
                Log.d("SaldoDebug", "Atualizando UI do cliente - Saldo formatado: $saldoFormatado")
                setTextColor(
                    ContextCompat.getColor(
                        context,
                        if (novoSaldo > 0) R.color.red else R.color.green
                    )
                )
            }
        }

        private fun setupExtratoAdapter(cliente: Cliente) {
            if (extratoAdapter == null) {
                extratoAdapter = ExtratoAdapter { venda ->
                    onExtratoItemClick(venda, cliente)
                }
                binding.rvExtrato.apply {
                    layoutManager = LinearLayoutManager(context)
                    adapter = extratoAdapter
                }
            }
        }

        private fun setupClickListeners(cliente: Cliente) {
            binding.root.setOnClickListener {
                val clienteId = cliente.id
                if (expandedClientes.contains(clienteId)) {
                    collapseExtrato()
                    expandedClientes.remove(clienteId)
                } else {
                    onClienteClick(clienteId)
                    expandedClientes.add(clienteId)
                    vendasPorCliente[clienteId]?.let { vendas ->
                        extratoAdapter?.submitList(vendas)
                        expandExtrato()
                    }
                }
            }
        }

        private fun atualizarExpandState(cliente: Cliente) {
            binding.layoutExtrato.visibility =
                if (expandedClientes.contains(cliente.id)) View.VISIBLE else View.GONE

            if (expandedClientes.contains(cliente.id)) {
                vendasPorCliente[cliente.id]?.let { vendas ->
                    extratoAdapter?.submitList(vendas)
                }
            }
        }

        @SuppressLint("LongLogTag")
        private fun construirHierarquiaLocal(cliente: Cliente) {
            coroutineScope.launch {
                try {
                    val locais = mutableListOf<String>()

                    withContext(Dispatchers.IO) {
                        val principal = localRepository.getLocalById(cliente.localId)
                        val sub1 = cliente.sublocal1Id?.let { localRepository.getLocalById(it) }
                        val sub2 = cliente.sublocal2Id?.let { localRepository.getLocalById(it) }
                        val sub3 = cliente.sublocal3Id?.let { localRepository.getLocalById(it) }

                        listOfNotNull(principal, sub1, sub2, sub3)
                            .sortedBy { it.level }
                            .forEach { local -> locais.add(local.nome) }
                    }

                    withContext(Dispatchers.Main) {
                        binding.tvLocalHierarquia.text = locais.joinToString(" > ")
                    }
                } catch (e: Exception) {
                    Log.e("ResultadosConsultaAdapter", "Erro ao buscar hierarquia: ${e.message}")
                    withContext(Dispatchers.Main) {
                        binding.tvLocalHierarquia.text = "Local não disponível"
                    }
                }
            }
        }

        private fun expandExtrato() {
            binding.layoutExtrato.apply {
                visibility = View.VISIBLE
                alpha = 0f
                animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        }

        private fun collapseExtrato() {
            binding.layoutExtrato.animate()
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    binding.layoutExtrato.visibility = View.GONE
                }
                .start()
        }
    }

    inner class LocalViewHolder(private val binding: ItemResultadoConsultaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(local: ResultadoConsulta.Local) {
            binding.apply {
                tvNomeCliente.text = local.local.nome
                tvTelefone.visibility = View.GONE
                tvLocalHierarquia.visibility = View.GONE
                tvValorDevido.visibility = View.GONE
                layoutExtrato.visibility = View.GONE
            }

            itemView.setOnClickListener { onLocalClick(local.local.id) }
        }
    }

    fun updateVendasPorCliente(novasVendas: Map<Long, List<Venda>>) {
        vendasPorCliente = novasVendas
        currentList.forEachIndexed { index, item ->
            if (item is ResultadoConsulta.Cliente && expandedClientes.contains(item.cliente.id)) {
                notifyItemChanged(index)
            }
        }
    }

    private class ResultadoConsultaDiffCallback : DiffUtil.ItemCallback<ResultadoConsulta>() {
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
            return when {
                oldItem is ResultadoConsulta.Local && newItem is ResultadoConsulta.Local ->
                    oldItem.local == newItem.local
                oldItem is ResultadoConsulta.Cliente && newItem is ResultadoConsulta.Cliente -> {
                    oldItem.cliente == newItem.cliente && oldItem.saldo == newItem.saldo
                }
                else -> false
            }
        }

        override fun getChangePayload(oldItem: ResultadoConsulta, newItem: ResultadoConsulta): Any? {
            return when {
                oldItem is ResultadoConsulta.Cliente &&
                        newItem is ResultadoConsulta.Cliente &&
                        oldItem.saldo != newItem.saldo -> {
                    SaldoPayload(newItem.saldo)
                }
                else -> null
            }
        }
    }

    private data class SaldoPayload(val novoSaldo: Double)

    companion object {
        private const val VIEW_TYPE_LOCAL = 0
        private const val VIEW_TYPE_CLIENTE = 1
    }
}