package com.example.caderneta.ui.consultas

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
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.util.Log
import android.widget.TextView
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

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.isNotEmpty()) {
            val payload = payloads[0] as? Bundle
            if (payload != null && holder is ClienteViewHolder) {
                val novoSaldo = payload.getDouble("saldo")
                holder.atualizarSaldo(novoSaldo)
                return
            }
        }
        onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ResultadoConsulta.Local -> (holder as LocalViewHolder).bind(item)
            is ResultadoConsulta.Cliente -> (holder as ClienteViewHolder).bind(item)
        }
    }

    inner class ClienteViewHolder(private val binding: ItemResultadoConsultaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var extratoAdapter: ExtratoAdapter? = null
        private var currentSaldo: Double = 0.0
        private var currentClienteId: Long = -1L

        fun bind(resultado: ResultadoConsulta.Cliente) {
            val cliente = resultado.cliente

            // Atualizar ID apenas se mudou
            if (currentClienteId != cliente.id) {
                currentClienteId = cliente.id
                configureExtratoAdapter(cliente)
            }

            // Configurar dados básicos
            binding.apply {
                tvNomeCliente.text = cliente.nome
                tvTelefone.text = cliente.telefone?.takeIf { it.isNotBlank() } ?: "Não informado"
                tvTelefone.visibility = View.VISIBLE
                tvLocalHierarquia.visibility = View.VISIBLE

                // Atualizar saldo apenas se mudou
                if (currentSaldo != resultado.saldo) {
                    atualizarSaldo(resultado.saldo)
                }

                updateExtratoVisibility(cliente)
            }

            // Setup de click apenas se não estiver configurado
            if (!binding.root.hasOnClickListeners()) {
                setupClickListeners(cliente)
            }

            // Carregar hierarquia local apenas se necessário
            if (binding.tvLocalHierarquia.text.isNullOrEmpty()) {
                construirHierarquiaLocal(cliente)
            }
        }

        fun atualizarSaldo(novoSaldo: Double) {
            if (currentSaldo != novoSaldo) {
                // Animar mudança do saldo
                val valorAntigo = currentSaldo
                currentSaldo = novoSaldo

                binding.tvValorDevido.animateValue(valorAntigo, novoSaldo)
                binding.tvValorDevido.setTextColor(
                    ContextCompat.getColor(
                        binding.root.context,
                        if (novoSaldo > 0) R.color.red else R.color.green
                    )
                )

                // Se o extrato estiver expandido, forçar atualização
                if (expandedClientes.contains(currentClienteId)) {
                    vendasPorCliente[currentClienteId]?.let { vendas ->
                        extratoAdapter?.submitList(vendas)
                    }
                }
            }
        }


        private fun configureExtratoAdapter(cliente: Cliente) {
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
                if (expandedClientes.contains(cliente.id)) {
                    collapseExtrato()
                    expandedClientes.remove(cliente.id)
                } else {
                    onClienteClick(cliente.id)
                    expandedClientes.add(cliente.id)
                    vendasPorCliente[cliente.id]?.let { vendas ->
                        extratoAdapter?.submitList(vendas)
                        expandExtrato()
                    }
                }
            }
        }

        private fun updateExtratoVisibility(cliente: Cliente) {
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
                            .forEach { local ->
                                locais.add(local.nome)
                            }
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

        private fun TextView.animateValue(startValue: Double, endValue: Double, duration: Long = 500L) {
            if (startValue == endValue) return  // Evita animação desnecessária

            val valueAnimator = ValueAnimator.ofFloat(startValue.toFloat(), endValue.toFloat())
            valueAnimator.duration = duration
            valueAnimator.interpolator = AccelerateDecelerateInterpolator()

            valueAnimator.addUpdateListener { animator ->
                val animatedValue = (animator.animatedValue as Float).toDouble()
                text = numberFormat.format(animatedValue)
            }

            valueAnimator.start()
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
        // Notificar apenas os itens que são clientes e estão expandidos
        currentList.forEachIndexed { index, item ->
            if (item is ResultadoConsulta.Cliente && expandedClientes.contains(item.cliente.id)) {
                notifyItemChanged(index)
            }
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
            return when {
                oldItem is ResultadoConsulta.Local && newItem is ResultadoConsulta.Local ->
                    oldItem == newItem
                oldItem is ResultadoConsulta.Cliente && newItem is ResultadoConsulta.Cliente -> {
                    oldItem.cliente == newItem.cliente && oldItem.saldo == newItem.saldo
                }
                else -> false
            }
        }

        override fun getChangePayload(oldItem: ResultadoConsulta, newItem: ResultadoConsulta): Any? {
            if (oldItem is ResultadoConsulta.Cliente && newItem is ResultadoConsulta.Cliente) {
                if (oldItem.cliente == newItem.cliente && oldItem.saldo != newItem.saldo) {
                    return Bundle().apply {
                        putDouble("saldo", newItem.saldo)
                    }
                }
            }
            return null
        }
    }

    companion object {
        private const val VIEW_TYPE_LOCAL = 0
        private const val VIEW_TYPE_CLIENTE = 1
    }
}