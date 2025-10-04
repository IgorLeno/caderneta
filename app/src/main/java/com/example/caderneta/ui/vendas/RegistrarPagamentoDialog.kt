package com.example.caderneta.ui.vendas

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.caderneta.databinding.DialogRegistrarPagamentoBinding

class RegistrarPagamentoDialog : DialogFragment() {

    private var _binding: DialogRegistrarPagamentoBinding? = null
    private val binding get() = requireNotNull(_binding) { "Binding should not be accessed before onCreateDialog or after onDestroyView" }

    var onPagamentoRegistrado: ((Double) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRegistrarPagamentoBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext())
            .setTitle("Registrar Pagamento")
            .setView(binding.root)
            .setPositiveButton("Registrar") { _, _ ->
                val valorPago = binding.etValorPago.text.toString().toDoubleOrNull() ?: 0.0
                onPagamentoRegistrado?.invoke(valorPago)
            }
            .setNegativeButton("Cancelar", null)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}