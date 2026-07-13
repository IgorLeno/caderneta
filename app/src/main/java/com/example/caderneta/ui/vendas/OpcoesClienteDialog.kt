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
import com.example.caderneta.ui.common.FeedbackPresenter
import com.example.caderneta.ui.consultas.ConsultasFragmentArgs
import com.example.caderneta.util.rethrowCancellation
import kotlinx.coroutines.launch

class OpcoesClienteDialog : DialogFragment() {
    companion object {
        const val DIALOG_TAG = "OpcoesClienteDialog"
        const val REQUEST_KEY = "opcoes_cliente_result"
        const val RESULT_ACTION = "action"
        const val RESULT_CLIENTE_ID = "clienteId"
        const val ACTION_EDIT = "edit"
        const val ACTION_DELETE = "delete"
        private const val ARG_CLIENTE_ID = "clienteId"
        private const val ARG_CLIENTE_NAME = "clienteName"

        fun newInstance(
            clienteId: Long,
            clienteName: String,
        ): OpcoesClienteDialog =
            OpcoesClienteDialog().apply {
                arguments =
                    Bundle().apply {
                        putLong(ARG_CLIENTE_ID, clienteId)
                        putString(ARG_CLIENTE_NAME, clienteName)
                    }
            }
    }

    private var _binding: DialogOpcoesClienteBinding? = null
    private val binding get() = _binding!!
    private val clienteId: Long get() = requireArguments().getLong(ARG_CLIENTE_ID)
    private val clienteName: String get() = requireArguments().getString(ARG_CLIENTE_NAME).orEmpty()
    private var cliente: Cliente? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesClienteBinding.inflate(LayoutInflater.from(context))

        return AlertDialog
            .Builder(
                requireContext(),
                com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert,
            ).setTitle(clienteName)
            .setView(binding.root)
            .setNegativeButton("Fechar") { _, _ -> dismiss() }
            .create()
            .apply {
                setOnShowListener {
                    lifecycleScope.launch {
                        cliente = getCliente()
                        val atual = cliente
                        if (atual == null) {
                            FeedbackPresenter.erro(conteudoActivity(), "Cliente não encontrado")
                            dismiss()
                        } else {
                            setupButtons(atual)
                        }
                    }
                }
            }
    }

    private suspend fun getCliente(): Cliente? =
        (requireActivity().application as CadernetaApplication).clienteRepository.getClienteById(clienteId)

    private suspend fun getLocalMaisEspecifico(cliente: Cliente): Local? {
        val localRepository = (requireActivity().application as CadernetaApplication).localRepository

        val locais =
            listOfNotNull(
                localRepository.getLocalById(cliente.localId),
                cliente.sublocal1Id?.let { localRepository.getLocalById(it) },
                cliente.sublocal2Id?.let { localRepository.getLocalById(it) },
                cliente.sublocal3Id?.let { localRepository.getLocalById(it) },
            )

        return locais.lastOrNull { local ->
            locais.takeWhile { it != local }.zipWithNext().all { (parent, child) ->
                child.level > parent.level
            }
        }
    }

    private fun setupButtons(cliente: Cliente) {
        binding.apply {
            btnConsultar.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val localMaisEspecifico = getLocalMaisEspecifico(cliente)
                        if (localMaisEspecifico != null) {
                            findNavController().navigate(
                                R.id.global_action_to_consultasFragment,
                                ConsultasFragmentArgs(
                                    clienteId = cliente.id,
                                    localId = localMaisEspecifico.id,
                                    filtroNomeCliente = cliente.nome,
                                ).toBundle(),
                            )
                            dismiss()
                        } else {
                            FeedbackPresenter.erro(conteudoActivity(), "Não foi possível determinar o local do cliente")
                        }
                    } catch (e: Exception) {
                        e.rethrowCancellation()
                        Log.e("OpcoesClienteDialog", "Erro ao navegar", e)
                        FeedbackPresenter.erro(conteudoActivity(), "Erro ao abrir consulta: ${e.message}")
                    }
                }
            }

            btnEditar.setOnClickListener {
                setActionResult(ACTION_EDIT, cliente.id)
                dismiss()
            }

            btnExcluir.setOnClickListener {
                showConfirmacaoExclusao(cliente)
            }
        }
    }

    private fun showConfirmacaoExclusao(cliente: Cliente) {
        AlertDialog
            .Builder(requireContext())
            .setTitle("Excluir Cliente")
            .setMessage("Tem certeza que deseja excluir ${cliente.nome}?")
            .setPositiveButton("Excluir") { _, _ ->
                setActionResult(ACTION_DELETE, cliente.id)
                dismiss()
            }.setNegativeButton("Cancelar", null)
            .show()
    }

    private fun setActionResult(
        action: String,
        clienteId: Long,
    ) {
        parentFragmentManager.setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(RESULT_ACTION, action)
                putLong(RESULT_CLIENTE_ID, clienteId)
            },
        )
    }

    private fun conteudoActivity(): android.view.View = requireActivity().findViewById(android.R.id.content)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
