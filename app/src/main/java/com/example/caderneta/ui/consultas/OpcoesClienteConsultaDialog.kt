package com.example.caderneta.ui.consultas

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.databinding.DialogOpcoesClienteBinding

class OpcoesConsultaClienteDialog(
    private val cliente: Cliente,
    private val onVenderClick: (Cliente) -> Unit,
    private val onEditarClick: (Cliente) -> Unit,
    private val onExcluirClick: (Cliente) -> Unit
) : DialogFragment() {

    private var _binding: DialogOpcoesClienteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesClienteBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext())
            .setTitle(cliente.nome)
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
                onVenderClick(cliente)
                dismiss()
            }

            btnEditar.setOnClickListener {
                onEditarClick(cliente)
                dismiss()
            }

            btnExcluir.setOnClickListener {
                showConfirmacaoExclusao()
            }
        }
    }

    private fun showConfirmacaoExclusao() {
        AlertDialog.Builder(requireContext())
            .setTitle("Excluir Cliente")
            .setMessage("Tem certeza que deseja excluir ${cliente.nome}?")
            .setPositiveButton("Excluir") { _, _ ->
                onExcluirClick(cliente)
                dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OpcoesConsultaClienteDialog"
    }
}