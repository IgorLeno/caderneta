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
import android.view.animation.AccelerateDecelerateInterpolator
import android.util.Log
import kotlinx.coroutines.launch

class ResultadosConsultaAdapter(
    private val onLocalClick: (Long) -> Unit,
    private val onClienteClick: (Long) -> Unit,
    private val onLimparExtratoClick: (Long) -> Unit,
    private val localRepository: com.example.caderneta.repository.LocalRepository
) : ListAdapter<ResultadoConsulta, RecyclerView.ViewHolder>(ResultadoConsultaDiffCallback()) {

    private var vendasPorCliente: Map<Long, List<Venda>> = emptyMap()
    private val expandedClientes = mutableSetOf<Long>()
    private val numberFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_LOCAL -> LocalViewHolder(
                ItemResultadoConsultaBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
            VIEW_TYPE_CLIENTE -> ClienteViewHolder(
                ItemResultadoConsultaBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
            )
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is LocalViewHolder -> holder.bind(getItem(position) as ResultadoConsulta.Local)
            is ClienteViewHolder -> holder.bind(getItem(position) as ResultadoConsulta.Cliente)
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

    inner class ClienteViewHolder(private val binding: ItemResultadoConsultaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var extratoAdapter: ExtratoAdapter? = null

        fun bind(resultado: ResultadoConsulta.Cliente) {
            val cliente = resultado.cliente

            binding.apply {
                // Informações básicas do cliente
                tvNomeCliente.text = cliente.nome
                tvTelefone.text = cliente.telefone ?: "-"

                // Valor devido
                tvValorDevido.text = numberFormat.format(resultado.saldo)
                tvValorDevido.setTextColor(
                    if (resultado.saldo > 0) android.graphics.Color.RED
                    else android.graphics.Color.GREEN
                )

                // Hierarquia de local
                construirHierarquiaLocal(cliente) { hierarquia ->
                    tvLocalHierarquia.text = hierarquia
                }

                // Setup do RecyclerView do extrato
                if (extratoAdapter == null) {
                    extratoAdapter = ExtratoAdapter()
                    rvExtrato.apply {
                        layoutManager = LinearLayoutManager(context)
                        adapter = extratoAdapter
                    }
                }

                // Configurar o clique no card
                root.setOnClickListener {
                    val clienteId = cliente.id
                    if (expandedClientes.contains(clienteId)) {
                        // Recolher
                        collapseExtrato()
                        expandedClientes.remove(clienteId)
                    } else {
                        // Expandir
                        onClienteClick(clienteId)
                        expandedClientes.add(clienteId)
                        vendasPorCliente[clienteId]?.let { vendas ->
                            extratoAdapter?.submitList(vendas)
                            expandExtrato()
                        }
                    }
                }

                // Atualizar visibilidade inicial do extrato
                layoutExtrato.visibility = if (expandedClientes.contains(cliente.id)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }

                // Atualizar dados do extrato se expandido
                if (expandedClientes.contains(cliente.id)) {
                    vendasPorCliente[cliente.id]?.let { vendas ->
                        extratoAdapter?.submitList(vendas)
                    }
                }
            }
        }

        @SuppressLint("LongLogTag")
        private fun construirHierarquiaLocal(cliente: Cliente, onComplete: (String) -> Unit) {
            val locais = mutableListOf<String>()

            // Buscar todos os locais necessários de forma assíncrona
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                try {
                    // Local principal
                    localRepository.getLocalById(cliente.localId)?.let {
                        locais.add(it.nome)
                    }

                    // Sublocal nível 1
                    cliente.sublocal1Id?.let { id ->
                        localRepository.getLocalById(id)?.let {
                            locais.add(it.nome)
                        }
                    }

                    // Sublocal nível 2
                    cliente.sublocal2Id?.let { id ->
                        localRepository.getLocalById(id)?.let {
                            locais.add(it.nome)
                        }
                    }

                    // Sublocal nível 3
                    cliente.sublocal3Id?.let { id ->
                        localRepository.getLocalById(id)?.let {
                            locais.add(it.nome)
                        }
                    }

                    // Montar hierarquia com separador
                    val hierarquia = locais.joinToString(" > ")
                    onComplete(hierarquia)

                } catch (e: Exception) {
                    Log.e("ResultadosConsultaAdapter", "Erro ao buscar hierarquia: ${e.message}")
                    onComplete(cliente.localId.toString())
                }
            }
        }

        private fun expandExtrato() {
            binding.layoutExtrato.apply {
                visibility = View.VISIBLE
                alpha = 0f
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = 300
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { animator ->
                        alpha = animator.animatedValue as Float
                    }
                    start()
                }
            }
        }

        private fun collapseExtrato() {
            binding.layoutExtrato.apply {
                ValueAnimator.ofFloat(1f, 0f).apply {
                    duration = 300
                    interpolator = AccelerateDecelerateInterpolator()
                    addUpdateListener { animator ->
                        alpha = animator.animatedValue as Float
                        if (animator.animatedValue as Float == 0f) {
                            visibility = View.GONE
                        }
                    }
                    start()
                }
            }
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
                    oldItem == newItem
                oldItem is ResultadoConsulta.Cliente && newItem is ResultadoConsulta.Cliente ->
                    oldItem.cliente == newItem.cliente && oldItem.saldo == newItem.saldo
                else -> false
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_LOCAL = 0
        private const val VIEW_TYPE_CLIENTE = 1
    }
}