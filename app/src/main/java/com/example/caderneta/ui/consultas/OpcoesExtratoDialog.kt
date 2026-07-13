package com.example.caderneta.ui.consultas

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.data.entity.Venda
import com.example.caderneta.databinding.DialogOpcoesExtratoBinding
import com.example.caderneta.util.rethrowCancellation
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

class OpcoesExtratoDialog : DialogFragment() {
    private var _binding: DialogOpcoesExtratoBinding? = null
    private val binding get() = _binding!!
    private val vendaId: Long get() = requireArguments().getLong(ARG_VENDA_ID)
    private val clienteId: Long get() = requireArguments().getLong(ARG_CLIENTE_ID)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesExtratoBinding.inflate(LayoutInflater.from(context))

        val dialog =
            AlertDialog
                .Builder(requireContext())
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
            btnEditarData.setOnClickListener {
                lifecycleScope.launch { showDatePicker() }
            }
            btnEditarOperacao.setOnClickListener {
                setActionResult(ACTION_EDIT_OPERATION)
                dismiss()
            }
            btnExcluir.setOnClickListener { showConfirmacaoExclusao() }
        }
    }

    private suspend fun showDatePicker() {
        val venda = getVendaAtual() ?: return
        val calendar = Calendar.getInstance().apply { time = venda.data }
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(
            requireContext(),
            { _, selectedYear, selectedMonth, selectedDay ->
                val newDate =
                    Calendar
                        .getInstance()
                        .apply {
                            set(selectedYear, selectedMonth, selectedDay)
                        }.time

                setActionResult(ACTION_EDIT_DATE, newDate)
                dismiss()
            },
            year,
            month,
            day,
        ).show()
    }

    private fun showConfirmacaoExclusao() {
        AlertDialog
            .Builder(requireContext())
            .setTitle("Confirmar Exclusão")
            .setMessage("Tem certeza que deseja excluir esta operação?")
            .setPositiveButton("Excluir") { _, _ ->
                setActionResult(ACTION_DELETE)
                dismiss()
            }.setNegativeButton("Cancelar", null)
            .show()
    }

    private suspend fun getVendaAtual(): Venda? =
        try {
            (requireActivity().application as CadernetaApplication).vendaRepository.getVendaById(vendaId)
        } catch (e: Exception) {
            e.rethrowCancellation()
            Log.e(TAG, "Erro ao carregar operação", e)
            null
        }

    private fun setActionResult(
        action: String,
        novaData: Date? = null,
    ) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_ACTION, action)
                putLong(RESULT_VENDA_ID, vendaId)
                putLong(RESULT_CLIENTE_ID, clienteId)
                novaData?.let { putLong(RESULT_DATE_MILLIS, it.time) }
            },
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OpcoesExtratoDialog"
        const val REQUEST_KEY = "opcoes_extrato_result"
        const val RESULT_ACTION = "action"
        const val RESULT_VENDA_ID = "vendaId"
        const val RESULT_CLIENTE_ID = "clienteId"
        const val RESULT_DATE_MILLIS = "dateMillis"
        const val ACTION_EDIT_DATE = "edit_date"
        const val ACTION_EDIT_OPERATION = "edit_operation"
        const val ACTION_DELETE = "delete"
        private const val ARG_VENDA_ID = "vendaId"
        private const val ARG_CLIENTE_ID = "clienteId"

        fun newInstance(
            vendaId: Long,
            clienteId: Long,
        ): OpcoesExtratoDialog =
            OpcoesExtratoDialog().apply {
                arguments =
                    Bundle().apply {
                        putLong(ARG_VENDA_ID, vendaId)
                        putLong(ARG_CLIENTE_ID, clienteId)
                    }
            }
    }
}
