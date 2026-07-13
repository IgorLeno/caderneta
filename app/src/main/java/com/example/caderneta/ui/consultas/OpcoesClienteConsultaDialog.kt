package com.example.caderneta.ui.consultas

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.caderneta.databinding.DialogOpcoesClienteBinding

class OpcoesConsultaClienteDialog : DialogFragment() {
    private var _binding: DialogOpcoesClienteBinding? = null
    private val binding get() = _binding!!
    private val clienteId: Long get() = requireArguments().getLong(ARG_CLIENTE_ID)
    private val clienteName: String get() = requireArguments().getString(ARG_CLIENTE_NAME).orEmpty()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesClienteBinding.inflate(LayoutInflater.from(context))

        return AlertDialog
            .Builder(requireContext())
            .setTitle(clienteName)
            .setView(binding.root)
            .setNegativeButton("Fechar", null)
            .create()
            .apply {
                setOnShowListener { setupButtons() }
            }
    }

    private fun setupButtons() {
        binding.apply {
            // Modificar o texto e ícone do botão consultar para vender
            btnConsultar.text = "Vender"
            btnConsultar.setIconResource(com.example.caderneta.R.drawable.ic_vendas)
            btnConsultar.setOnClickListener {
                setActionResult(ACTION_SELL)
                dismiss()
            }

            btnEditar.setOnClickListener {
                setActionResult(ACTION_EDIT)
                dismiss()
            }

            btnExcluir.setOnClickListener {
                showConfirmacaoExclusao()
            }
        }
    }

    private fun showConfirmacaoExclusao() {
        AlertDialog
            .Builder(requireContext())
            .setTitle("Excluir Cliente")
            .setMessage("Tem certeza que deseja excluir $clienteName?")
            .setPositiveButton("Excluir") { _, _ ->
                setActionResult(ACTION_DELETE)
                dismiss()
            }.setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setActionResult(action: String) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_ACTION, action)
                putLong(RESULT_CLIENTE_ID, clienteId)
            },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OpcoesConsultaClienteDialog"
        const val REQUEST_KEY = "opcoes_consulta_cliente_result"
        const val RESULT_ACTION = "action"
        const val RESULT_CLIENTE_ID = "clienteId"
        const val ACTION_SELL = "sell"
        const val ACTION_EDIT = "edit"
        const val ACTION_DELETE = "delete"
        private const val ARG_CLIENTE_ID = "clienteId"
        private const val ARG_CLIENTE_NAME = "clienteName"

        fun newInstance(
            clienteId: Long,
            clienteName: String,
        ): OpcoesConsultaClienteDialog =
            OpcoesConsultaClienteDialog().apply {
                arguments =
                    Bundle().apply {
                        putLong(ARG_CLIENTE_ID, clienteId)
                        putString(ARG_CLIENTE_NAME, clienteName)
                    }
            }
    }
}
