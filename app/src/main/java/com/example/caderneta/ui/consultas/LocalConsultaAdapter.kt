package com.example.caderneta.ui.consultas

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.caderneta.R
import com.example.caderneta.data.entity.Local
import com.example.caderneta.databinding.ItemLocalConsultaBinding

class LocalConsultaAdapter(
    private val onLocalClick: (Local) -> Unit,
    private val onToggleExpand: (Local) -> Unit
) : ListAdapter<LocalWithChildren, LocalConsultaAdapter.LocalViewHolder>(LocalDiffCallback()) {

    private var allLocals: List<LocalWithChildren> = emptyList()

    inner class LocalViewHolder(private val binding: ItemLocalConsultaBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(localWithChildren: LocalWithChildren) {
            val local = localWithChildren.local
            binding.tvNomeLocal.text = local.nome
            binding.root.setPadding(16 + local.level * 32, 8, 16, 8)

            binding.root.setOnClickListener { onLocalClick(local) }

            // Configura o botão de expandir/retrair
            binding.btnExpandir.apply {
                visibility = if (localWithChildren.hasChildren) android.view.View.VISIBLE
                else android.view.View.INVISIBLE
                setImageResource(
                    if (local.isExpanded) R.drawable.ic_chevron_down
                    else R.drawable.ic_chevron_right
                )
                setOnClickListener { onToggleExpand(local) }
            }

            // Define a cor de fundo com base no nível
            val colorRes = when (local.level) {
                0 -> R.color.level_0
                1 -> R.color.level_1
                2 -> R.color.level_2
                else -> R.color.level_3
            }
            itemView.setBackgroundColor(ContextCompat.getColor(itemView.context, colorRes))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LocalViewHolder {
        val binding = ItemLocalConsultaBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return LocalViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LocalViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun updateLocais(locais: List<Local>) {
        allLocals = locais.map { local ->
            LocalWithChildren(local, locais.any { it.parentId == local.id })
        }
        val flattenedList = flattenLocals(allLocals)
        submitList(flattenedList)
    }

    private fun flattenLocals(locais: List<LocalWithChildren>): List<LocalWithChildren> {
        val result = mutableListOf<LocalWithChildren>()
        val topLevelLocals = locais.filter { it.local.parentId == null }

        for (local in topLevelLocals) {
            result.add(local)
            if (local.local.isExpanded) {
                result.addAll(getAllChildrenRecursive(local, locais))
            }
        }

        return result
    }

    private fun getAllChildrenRecursive(
        parent: LocalWithChildren,
        allLocals: List<LocalWithChildren>
    ): List<LocalWithChildren> {
        val result = mutableListOf<LocalWithChildren>()
        val directChildren = allLocals.filter { it.local.parentId == parent.local.id }
        for (child in directChildren) {
            result.add(child)
            if (child.local.isExpanded) {
                result.addAll(getAllChildrenRecursive(child, allLocals))
            }
        }
        return result
    }

    private class LocalDiffCallback : DiffUtil.ItemCallback<LocalWithChildren>() {
        override fun areItemsTheSame(
            oldItem: LocalWithChildren,
            newItem: LocalWithChildren
        ): Boolean {
            return oldItem.local.id == newItem.local.id
        }

        override fun areContentsTheSame(
            oldItem: LocalWithChildren,
            newItem: LocalWithChildren
        ): Boolean {
            return oldItem == newItem
        }
    }
}

data class LocalWithChildren(val local: Local, val hasChildren: Boolean)