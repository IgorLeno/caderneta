package com.example.caderneta.ui.vendas

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.caderneta.data.entity.Local
import com.example.caderneta.databinding.DialogOpcoesLocalBinding

class OpcoesLocalDialog(
    private val local: Local,
    private val onAddSubLocal: (Local) -> Unit,
    private val onEditLocal: (Local) -> Unit,
    private val onDeleteLocal: (Local) -> Unit,
) : DialogFragment() {
    companion object {
        const val DIALOG_TAG = "OpcoesLocalDialog"
    }

    private var _binding: DialogOpcoesLocalBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesLocalBinding.inflate(LayoutInflater.from(context))

        return AlertDialog
            .Builder(
                requireContext(),
                com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert,
            ).setTitle(local.nome)
            .setView(binding.root)
            .setNegativeButton("Fechar") { _, _ -> dismiss() }
            .create()
            .apply {
                setOnShowListener { setupButtons() }
            }
    }

    private fun setupButtons() {
        binding.apply {
            btnAdicionarSublocal.setOnClickListener {
                closeMenuThen { onAddSubLocal(local) }
            }

            btnRenomearLocal.setOnClickListener {
                closeMenuThen { onEditLocal(local) }
            }

            btnExcluirLocal.setOnClickListener {
                closeMenuThen { onDeleteLocal(local) }
            }
        }
    }

    private fun closeMenuThen(action: () -> Unit) {
        dismiss()
        parentFragmentManager.executePendingTransactions()
        action()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
