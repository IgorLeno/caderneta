package com.example.caderneta.ui.consultas

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavOptions
import androidx.navigation.Navigation.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation
import com.example.caderneta.R
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.ItemResultadoConsultaBinding
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.util.centavosParaReais
import com.example.caderneta.util.rethrowCancellation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.example.caderneta.data.entity.Cliente as ClienteEntity

@Suppress("LongParameterList")
class ResultadosConsultaAdapter(
    private val onLocalClick: (Long) -> Unit,
    private val onClienteClick: (Long) -> Unit,
    private val onExtratoItemClick: (Venda, ClienteEntity) -> Unit,
    private val localRepository: LocalRepository,
    private val coroutineScope: CoroutineScope,
    private val fragmentManager: FragmentManager,
    private val onEditarCliente: (ClienteEntity) -> Unit,
    private val onExcluirCliente: (ClienteEntity) -> Unit,
    private val onClienteCollapse: (Long) -> Unit,
    private val getClientePhotoFile: (String?) -> File?,
) : ListAdapter<ResultadoConsulta, RecyclerView.ViewHolder>(ResultadoConsultaDiffCallback()) {
    private var vendasPorCliente: Map<Long, List<Venda>> = emptyMap()
    private val expandedClientes = mutableSetOf<Long>()

    override fun getItemViewType(position: Int): Int =
        when (getItem(position)) {
            is ResultadoConsulta.Local -> VIEW_TYPE_LOCAL
            is ResultadoConsulta.Cliente -> VIEW_TYPE_CLIENTE
        }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecyclerView.ViewHolder {
        val binding =
            ItemResultadoConsultaBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
        return when (viewType) {
            VIEW_TYPE_LOCAL -> LocalViewHolder(binding)
            VIEW_TYPE_CLIENTE -> ClienteViewHolder(binding)
            else -> throw IllegalArgumentException("Invalid view type")
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<Any>,
    ) {
        if (payloads.isNotEmpty()) {
            val payload = payloads[0] as? Bundle
            if (payload != null && holder is ClienteViewHolder) {
                val novoSaldo = payload.getLong("saldoCentavos")
                holder.atualizarSaldo(novoSaldo)
                return
            }
        }
        onBindViewHolder(holder, position)
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        when (val item = getItem(position)) {
            is ResultadoConsulta.Local -> (holder as LocalViewHolder).bind(item)
            is ResultadoConsulta.Cliente -> (holder as ClienteViewHolder).bind(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is ClienteViewHolder) {
            holder.onRecycled()
        }
        super.onViewRecycled(holder)
    }

    inner class ClienteViewHolder(
        private val binding: ItemResultadoConsultaBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private var extratoAdapter: ExtratoAdapter? = null
        private var currentSaldoCentavos: Long = 0
        private var currentClienteId: Long = -1L
        private var hierarchyJob: Job? = null

        @SuppressLint("LongLogTag")
        @Suppress("LongMethod")
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
                tvLocalHierarquia.text = ""
                tvValorDevido.visibility = View.VISIBLE
                ivExpandir.visibility = View.VISIBLE
                ivClienteFoto.contentDescription =
                    root.context.getString(R.string.cliente_photo_content_description, cliente.nome)
                val fotoSizePx = ivClienteFoto.fixedImageSizePx()
                ivClienteFoto.load(getClientePhotoFile(cliente.fotoNome)) {
                    size(fotoSizePx, fotoSizePx)
                    placeholder(R.drawable.ic_avatar)
                    error(R.drawable.ic_avatar)
                    transformations(CircleCropTransformation())
                }

                // Atualizar saldo apenas se mudou
                if (currentSaldoCentavos != resultado.saldoCentavos) {
                    atualizarSaldo(resultado.saldoCentavos)
                }

                ivExpandir.rotation =
                    if (expandedClientes.contains(cliente.id)) ROTATION_EXPANDIDO else ROTATION_COLAPSADO
                updateExtratoVisibility(cliente)
            }

            setupClickListeners(cliente)

            // Setup do long click listener
            binding.root.setOnLongClickListener {
                OpcoesConsultaClienteDialog(
                    cliente = cliente,
                    onVenderClick = { clienteSelecionado ->
                        coroutineScope.launch {
                            try {
                                val localMaisEspecifico =
                                    withContext(Dispatchers.IO) {
                                        listOfNotNull(
                                            localRepository.getLocalById(clienteSelecionado.localId),
                                            clienteSelecionado.sublocal1Id?.let { localRepository.getLocalById(it) },
                                            clienteSelecionado.sublocal2Id?.let { localRepository.getLocalById(it) },
                                            clienteSelecionado.sublocal3Id?.let { localRepository.getLocalById(it) },
                                        ).lastOrNull()
                                    }

                                val navController = findNavController(binding.root)
                                val destinationListener =
                                    object : NavController.OnDestinationChangedListener {
                                        override fun onDestinationChanged(
                                            controller: NavController,
                                            destination: NavDestination,
                                            arguments: Bundle?,
                                        ) {
                                            if (destination.id == R.id.vendasFragment) {
                                                controller.removeOnDestinationChangedListener(this)
                                                localMaisEspecifico?.let { local ->
                                                    onLocalClick(local.id)
                                                }
                                            }
                                        }
                                    }
                                navController.addOnDestinationChangedListener(destinationListener)
                                navController.navigate(
                                    R.id.vendasFragment,
                                    null,
                                    NavOptions
                                        .Builder()
                                        .setPopUpTo(R.id.consultasFragment, true)
                                        .build(),
                                )
                            } catch (e: Exception) {
                                e.rethrowCancellation()
                                Log.e("ResultadosConsultaAdapter", "Erro ao navegar para vendas", e)
                            }
                        }
                    },
                    onEditarClick = { onEditarCliente(it) },
                    onExcluirClick = { onExcluirCliente(it) },
                ).show(fragmentManager, OpcoesConsultaClienteDialog.TAG)
                true
            }

            construirHierarquiaLocal(cliente)
        }

        fun onRecycled() {
            hierarchyJob?.cancel()
            hierarchyJob = null
            binding.tvLocalHierarquia.text = ""
        }

        fun atualizarSaldo(novoSaldoCentavos: Long) {
            if (currentSaldoCentavos != novoSaldoCentavos) {
                // Animar mudança do saldo
                val valorAntigo = currentSaldoCentavos
                currentSaldoCentavos = novoSaldoCentavos

                binding.tvValorDevido.animateValue(valorAntigo, novoSaldoCentavos)
                binding.tvValorDevido.setTextColor(
                    ContextCompat.getColor(
                        binding.root.context,
                        if (novoSaldoCentavos > 0) R.color.red else R.color.green,
                    ),
                )

                // Se o extrato estiver expandido, forçar atualização
                if (expandedClientes.contains(currentClienteId)) {
                    vendasPorCliente[currentClienteId]?.let { vendas ->
                        extratoAdapter?.submitList(vendas)
                    }
                }
            }
        }

        private fun configureExtratoAdapter(cliente: ClienteEntity) {
            extratoAdapter =
                ExtratoAdapter { venda ->
                    onExtratoItemClick(venda, cliente)
                }
            binding.rvExtrato.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = extratoAdapter
            }
        }

        private fun setupClickListeners(cliente: ClienteEntity) {
            binding.root.setOnClickListener {
                if (expandedClientes.contains(cliente.id)) {
                    collapseExtrato()
                    expandedClientes.remove(cliente.id)
                    onClienteCollapse(cliente.id)
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

        private fun updateExtratoVisibility(cliente: ClienteEntity) {
            binding.layoutExtrato.visibility =
                if (expandedClientes.contains(cliente.id)) View.VISIBLE else View.GONE

            if (expandedClientes.contains(cliente.id)) {
                vendasPorCliente[cliente.id]?.let { vendas ->
                    extratoAdapter?.submitList(vendas)
                }
            }
        }

        @SuppressLint("LongLogTag")
        private fun construirHierarquiaLocal(cliente: ClienteEntity) {
            hierarchyJob?.cancel()
            val clienteId = cliente.id
            hierarchyJob =
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
                            if (currentClienteId == clienteId) {
                                binding.tvLocalHierarquia.text = locais.joinToString(" > ")
                            }
                        }
                    } catch (e: Exception) {
                        e.rethrowCancellation()
                        Log.e("ResultadosConsultaAdapter", "Erro ao buscar hierarquia", e)
                        withContext(Dispatchers.Main) {
                            if (currentClienteId == clienteId) {
                                binding.tvLocalHierarquia.text = "Local não disponível"
                            }
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
            binding.ivExpandir
                .animate()
                .rotation(ROTATION_EXPANDIDO)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        private fun collapseExtrato() {
            binding.layoutExtrato
                .animate()
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    binding.layoutExtrato.visibility = View.GONE
                }.start()
            binding.ivExpandir
                .animate()
                .rotation(ROTATION_COLAPSADO)
                .setDuration(300)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }

        private fun TextView.animateValue(
            startValue: Long,
            endValue: Long,
            duration: Long = 500L,
        ) {
            if (startValue == endValue) return // Evita animação desnecessária

            val valueAnimator = ValueAnimator.ofFloat(startValue.toFloat(), endValue.toFloat())
            valueAnimator.duration = duration
            valueAnimator.interpolator = AccelerateDecelerateInterpolator()

            valueAnimator.addUpdateListener { animator ->
                val animatedValue = (animator.animatedValue as Float).toLong()
                text = animatedValue.centavosParaReais()
            }

            valueAnimator.start()
        }
    }

    inner class LocalViewHolder(
        private val binding: ItemResultadoConsultaBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(local: ResultadoConsulta.Local) {
            binding.apply {
                tvNomeCliente.text = local.local.nome
                tvTelefone.visibility = View.GONE
                tvLocalHierarquia.visibility = View.GONE
                tvValorDevido.visibility = View.GONE
                ivExpandir.visibility = View.GONE
                ivClienteFoto.setImageResource(R.drawable.ic_location)
                ivClienteFoto.contentDescription =
                    root.context.getString(R.string.local_result_content_description, local.local.nome)
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
        override fun areItemsTheSame(
            oldItem: ResultadoConsulta,
            newItem: ResultadoConsulta,
        ): Boolean =
            when {
                oldItem is ResultadoConsulta.Local && newItem is ResultadoConsulta.Local ->
                    oldItem.local.id == newItem.local.id
                oldItem is ResultadoConsulta.Cliente && newItem is ResultadoConsulta.Cliente ->
                    oldItem.cliente.id == newItem.cliente.id
                else -> false
            }

        override fun areContentsTheSame(
            oldItem: ResultadoConsulta,
            newItem: ResultadoConsulta,
        ): Boolean =
            when {
                oldItem is ResultadoConsulta.Local && newItem is ResultadoConsulta.Local ->
                    oldItem == newItem
                oldItem is ResultadoConsulta.Cliente && newItem is ResultadoConsulta.Cliente -> {
                    oldItem.cliente == newItem.cliente && oldItem.saldoCentavos == newItem.saldoCentavos
                }
                else -> false
            }

        override fun getChangePayload(
            oldItem: ResultadoConsulta,
            newItem: ResultadoConsulta,
        ): Any? {
            if (oldItem is ResultadoConsulta.Cliente && newItem is ResultadoConsulta.Cliente) {
                if (oldItem.cliente == newItem.cliente && oldItem.saldoCentavos != newItem.saldoCentavos) {
                    return Bundle().apply {
                        putLong("saldoCentavos", newItem.saldoCentavos)
                    }
                }
            }
            return null
        }
    }

    companion object {
        private const val VIEW_TYPE_LOCAL = 0
        private const val VIEW_TYPE_CLIENTE = 1
        private const val ROTATION_COLAPSADO = 0f
        private const val ROTATION_EXPANDIDO = 180f
        private const val DEFAULT_RESULTADO_PHOTO_DP = 44f

        private fun View.fixedImageSizePx(): Int =
            layoutParams.width.takeIf { it > 0 }
                ?: (resources.displayMetrics.density * DEFAULT_RESULTADO_PHOTO_DP).toInt()
    }
}
