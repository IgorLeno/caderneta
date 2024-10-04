package com.example.caderneta.ui.vendas

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.caderneta.databinding.DialogVendaPersonalizadaBinding

class VendaPersonalizadaDialog : DialogFragment() {

    private var _binding: DialogVendaPersonalizadaBinding? = null
    private val binding get() = _binding!!

    var onVendaPersonalizadaRegistrada: ((Int, Int, Double) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogVendaPersonalizadaBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext())
            .setTitle("Venda Personalizada")
            .setView(binding.root)
            .setPositiveButton("Registrar") { _, _ ->
                val quantidadeSalgados = binding.etQuantidadeSalgados.text.toString().toIntOrNull() ?: 0
                val quantidadeSucos = binding.etQuantidadeSucos.text.toString().toIntOrNull() ?: 0
                val valorTotal = binding.etValorTotal.text.toString().toDoubleOrNull() ?: 0.0
                onVendaPersonalizadaRegistrada?.invoke(quantidadeSalgados, quantidadeSucos, valorTotal)
            }
            .setNegativeButton("Cancelar", null)
            .create()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}