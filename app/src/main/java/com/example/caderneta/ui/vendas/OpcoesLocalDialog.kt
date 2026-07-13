package com.example.caderneta.ui.vendas

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.caderneta.databinding.DialogOpcoesLocalBinding

class OpcoesLocalDialog : DialogFragment() {
    companion object {
        const val DIALOG_TAG = "OpcoesLocalDialog"
        const val REQUEST_KEY = "opcoes_local_result"
        const val RESULT_ACTION = "action"
        const val RESULT_LOCAL_ID = "localId"
        const val ACTION_ADD_SUBLOCAL = "add_sublocal"
        const val ACTION_EDIT = "edit"
        const val ACTION_DELETE = "delete"
        private const val ARG_LOCAL_ID = "localId"
        private const val ARG_LOCAL_NAME = "localName"

        fun newInstance(
            localId: Long,
            localName: String,
        ): OpcoesLocalDialog =
            OpcoesLocalDialog().apply {
                arguments =
                    Bundle().apply {
                        putLong(ARG_LOCAL_ID, localId)
                        putString(ARG_LOCAL_NAME, localName)
                    }
            }
    }

    private var _binding: DialogOpcoesLocalBinding? = null
    private val binding get() = _binding!!
    private val localId: Long get() = requireArguments().getLong(ARG_LOCAL_ID)
    private val localName: String get() = requireArguments().getString(ARG_LOCAL_NAME).orEmpty()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesLocalBinding.inflate(LayoutInflater.from(context))

        return AlertDialog
            .Builder(
                requireContext(),
                com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert,
            ).setTitle(localName)
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
                closeMenuThen(ACTION_ADD_SUBLOCAL)
            }

            btnRenomearLocal.setOnClickListener {
                closeMenuThen(ACTION_EDIT)
            }

            btnExcluirLocal.setOnClickListener {
                closeMenuThen(ACTION_DELETE)
            }
        }
    }

    private fun closeMenuThen(action: String) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_ACTION, action)
                putLong(RESULT_LOCAL_ID, localId)
            },
        )
        dismiss()
        parentFragmentManager.executePendingTransactions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
