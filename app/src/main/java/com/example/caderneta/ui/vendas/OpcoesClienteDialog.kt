package com.example.caderneta.ui.vendas

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.R
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.databinding.DialogOpcoesClienteBinding
import com.example.caderneta.ui.consultas.ConsultasFragmentArgs
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class OpcoesClienteDialog(
    private val cliente: Cliente,
    private val onEditarClick: (Cliente) -> Unit,
    private val onExcluirClick: (Cliente) -> Unit
) : DialogFragment() {

    companion object {
        const val DIALOG_TAG = "OpcoesClienteDialog"
    }

    private var _binding: DialogOpcoesClienteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesClienteBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext(),
            com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
            .setTitle(cliente.nome)
            .setView(binding.root)
            .setNegativeButton("Fechar") { _, _ -> dismiss() }
            .create()
            .apply {
                setOnShowListener { setupButtons() }
            }
    }

    private suspend fun getLocalMaisEspecifico(): Local? {
        val localRepository = (requireActivity().application as CadernetaApplication).localRepository

        val locais = listOfNotNull(
            localRepository.getLocalById(cliente.localId),
            cliente.sublocal1Id?.let { localRepository.getLocalById(it) },
            cliente.sublocal2Id?.let { localRepository.getLocalById(it) },
            cliente.sublocal3Id?.let { localRepository.getLocalById(it) }
        )

        return locais.lastOrNull { local ->
            locais.takeWhile { it != local }.zipWithNext().all { (parent, child) ->
                child.level > parent.level
            }
        }
    }

    private fun setupButtons() {
        binding.apply {
            btnConsultar.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val localMaisEspecifico = getLocalMaisEspecifico()
                        if (localMaisEspecifico != null) {
                            findNavController().navigate(
                                R.id.global_action_to_consultasFragment,
                                ConsultasFragmentArgs(
                                    clienteId = cliente.id,
                                    localId = localMaisEspecifico.id,
                                    filtroNomeCliente = cliente.nome
                                ).toBundle()
                            )
                            dismiss()
                        } else {
                            Snackbar.make(
                                requireView(),
                                "Não foi possível determinar o local do cliente",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("OpcoesClienteDialog", "Erro ao navegar", e)
                        Snackbar.make(
                            requireView(),
                            "Erro ao abrir consulta: ${e.message}",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
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
}