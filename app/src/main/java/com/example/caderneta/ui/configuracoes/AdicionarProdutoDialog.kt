package com.example.caderneta.ui.configuracoes

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.caderneta.R
import com.example.caderneta.data.entity.TipoProduto
import com.example.caderneta.databinding.DialogAdicionarProdutoBinding

class AdicionarProdutoDialog : DialogFragment() {

    private var _binding: DialogAdicionarProdutoBinding? = null
    private val binding get() = _binding!!

    var onProdutoAdicionado: ((String, Double, TipoProduto) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogAdicionarProdutoBinding.inflate(LayoutInflater.from(context))

        setupTipoProdutoSpinner()

        return AlertDialog.Builder(requireContext())
            .setTitle("Adicionar Novo Produto")
            .setView(binding.root)
            .setPositiveButton("Adicionar", null) // Será configurado depois
            .setNegativeButton("Cancelar", null)
            .create()
            .apply {
                setOnShowListener { dialog ->
                    (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (validateInputs()) {
                            val nome = binding.etNomeProduto.text.toString()
                            val preco = binding.etPrecoProduto.text.toString().toDoubleOrNull() ?: 0.0
                            val tipo = when (binding.spinnerTipoProduto.selectedItemPosition) {
                                0 -> TipoProduto.SALGADO
                                1 -> TipoProduto.SUCO
                                else -> TipoProduto.SALGADO
                            }
                            onProdutoAdicionado?.invoke(nome, preco, tipo)
                            dismiss()
                        }
                    }
                }
            }
    }

    private fun setupTipoProdutoSpinner() {
        val tiposProduto = arrayOf("Salgado", "Suco")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, tiposProduto)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerTipoProduto.adapter = adapter
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (binding.etNomeProduto.text.isNullOrBlank()) {
            binding.etNomeProduto.error = "Nome é obrigatório"
            isValid = false
        }

        if (binding.etPrecoProduto.text.isNullOrBlank() || binding.etPrecoProduto.text.toString().toDoubleOrNull() == null) {
            binding.etPrecoProduto.error = "Preço inválido"
            isValid = false
        }

        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}