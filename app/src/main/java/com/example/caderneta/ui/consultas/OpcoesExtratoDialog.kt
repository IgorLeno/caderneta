package com.example.caderneta.ui.consultas

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.DialogOpcoesExtratoBinding
import android.widget.Toast
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class OpcoesExtratoDialog(
    private val venda: Venda,
    private val cliente: Cliente,
    private val onEditarData: suspend (Venda, Date) -> Boolean,
    private val onEditarOperacao: (Venda) -> Unit,
    private val onExcluir: suspend (Venda) -> Boolean
) : DialogFragment() {

    private var _binding: DialogOpcoesExtratoBinding? = null
    private val binding get() = _binding!!

    private val dateFormatter = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesExtratoBinding.inflate(LayoutInflater.from(context))

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Opções")
            .setView(binding.root)
            .setNegativeButton("Fechar", null)
            .create()

        dialog.setCanceledOnTouchOutside(false)

        setupButtons()

        return dialog
    }

    private fun setupButtons() {
        binding.apply {
            btnEditarData.setOnClickListener { showDatePicker() }
            btnEditarOperacao.setOnClickListener { showEditarOperacao() }
            btnExcluir.setOnClickListener { showConfirmacaoExclusao() }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance().apply { time = venda.data }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val newDate = Calendar.getInstance().apply {
                    set(selectedYear, selectedMonth, selectedDay)
                }.time

                lifecycleScope.launch {
                    try {
                        if (onEditarData(venda, newDate)) {
                            showFeedback("Data atualizada com sucesso")
                            dismiss()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao atualizar data", e)
                        showFeedback("Erro ao atualizar data: ${e.message}", isError = true)
                    }
                }
            },
            year,
            month,
            day
        ).show()
    }

    private fun showEditarOperacao() {
        onEditarOperacao(venda)
        dismiss()
    }

    private fun showConfirmacaoExclusao() {
        AlertDialog.Builder(requireContext())
            .setTitle("Confirmar Exclusão")
            .setMessage("Tem certeza que deseja excluir esta operação?")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    try {
                        if (onExcluir(venda)) {
                            showFeedback("Operação excluída com sucesso")
                            dismiss()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro ao excluir operação", e)
                        showFeedback("Erro ao excluir operação: ${e.message}", isError = true)
                    }
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showFeedback(message: String, isError: Boolean = false) {
        val context = context ?: return
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OpcoesExtratoDialog"
    }
}