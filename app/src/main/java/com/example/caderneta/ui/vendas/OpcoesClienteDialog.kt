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
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.databinding.DialogOpcoesClienteBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class OpcoesClienteDialog(
    private val cliente: Cliente,
    private val onEditarClick: (Cliente) -> Unit,
    private val onExcluirClick: (Cliente) -> Unit
) : DialogFragment() {

    private var _binding: DialogOpcoesClienteBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogOpcoesClienteBinding.inflate(LayoutInflater.from(context))

        // Criar dialog com tema personalizado para garantir estilos corretos
        return AlertDialog.Builder(requireContext(), com.google.android.material.R.style.ThemeOverlay_MaterialComponents_Dialog_Alert)
            .setTitle(cliente.nome)
            .setView(binding.root)
            .setNegativeButton("Fechar") { _, _ -> dismiss() }
            .create()
            .apply {
                // Configurar botões assim que o dialog for criado
                setOnShowListener {
                    setupButtons()
                }
            }
    }

    private suspend fun getLocalMaisEspecifico(): Local? {
        val localRepository = (requireActivity().application as CadernetaApplication).localRepository

        // Buscar e validar cada nível da hierarquia
        val local3 = cliente.sublocal3Id?.let { localRepository.getLocalById(it) }
        val local2 = cliente.sublocal2Id?.let { localRepository.getLocalById(it) }
        val local1 = cliente.sublocal1Id?.let { localRepository.getLocalById(it) }
        val localPrincipal = localRepository.getLocalById(cliente.localId)

        // Validar a hierarquia e retornar o local mais específico válido
        return when {
            local3 != null && validarHierarquia(localPrincipal, local1, local2, local3) -> local3
            local2 != null && validarHierarquia(localPrincipal, local1, local2) -> local2
            local1 != null && validarHierarquia(localPrincipal, local1) -> local1
            localPrincipal != null -> localPrincipal
            else -> null
        }
    }

    private fun validarHierarquia(vararg locais: Local?): Boolean {
        // Verifica se a sequência de níveis é válida
        return locais.filterNotNull()
            .zipWithNext()
            .all { (parent, child) -> child.level > parent.level }
    }

    private fun setupButtons() {
        binding.apply {
            btnConsultar.setOnClickListener {
                lifecycleScope.launch {
                    try {
                        val localMaisEspecifico = getLocalMaisEspecifico()
                        if (localMaisEspecifico != null) {
                            Log.d("OpcoesClienteDialog",
                                "Navegando para consultas com local: ${localMaisEspecifico.nome} (${localMaisEspecifico.id})")

                            val action = VendasFragmentDirections.actionVendasFragmentToConsultasFragment(
                                clienteId = cliente.id,
                                localId = localMaisEspecifico.id,
                                filtroNomeCliente = cliente.nome
                            )
                            findNavController().navigate(action)
                            dismiss()
                        } else {
                            Log.e("OpcoesClienteDialog", "Local mais específico não encontrado")
                            // Mostrar mensagem de erro
                            Snackbar.make(
                                requireView(),
                                "Não foi possível determinar o local do cliente",
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    } catch (e: Exception) {
                        Log.e("OpcoesClienteDialog", "Erro ao navegar", e)
                        // Mostrar mensagem de erro
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

    companion object {
        const val TAG = "OpcoesClienteDialog"
    }
}