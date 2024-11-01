package com.example.caderneta.ui.vendas

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Local
import com.example.caderneta.databinding.DialogNovoClienteBinding
import com.example.caderneta.databinding.DialogOpcoesClienteBinding
import com.example.caderneta.viewmodel.VendasViewModel
import kotlinx.coroutines.launch

class NovoClienteDialog(private val viewModel: VendasViewModel) : DialogFragment() {
    private var _binding: DialogNovoClienteBinding? = null
    private val binding get() = _binding!!

    var onClienteAdicionado: ((String, String, Long, Long?, Long?, Long?) -> Unit)? = null

    private lateinit var localAdapter: ArrayAdapter<Local>
    private lateinit var sublocal1Adapter: ArrayAdapter<Local>
    private lateinit var sublocal2Adapter: ArrayAdapter<Local>
    private lateinit var sublocal3Adapter: ArrayAdapter<Local>

    // Cache para manter a hierarquia importada
    private var importedHierarchy: List<Local>? = null


    private var clienteExistente: Cliente? = null

    fun setClienteExistente(cliente: Cliente) {
        clienteExistente = cliente
    }


    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogNovoClienteBinding.inflate(LayoutInflater.from(context))  // Correção aqui

        return AlertDialog.Builder(requireContext())
            .setTitle(if (clienteExistente != null) "Editar Cliente" else "Novo Cliente")
            .setView(binding.root)
            .setPositiveButton(if (clienteExistente != null) "Salvar" else "Adicionar", null)
            .setNegativeButton("Cancelar", null)
            .create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInputValidation()
        setupSpinners()
        setupDialogButtons()
        setupImportButton()

        // Preencher campos se for edição
        clienteExistente?.let { cliente ->
            // Preencher apenas nome e telefone
            binding.etNome.setText(cliente.nome)
            binding.etTelefone.setText(cliente.telefone)

            // Limpar campos de local
            binding.spinnerLocal.text = null
            binding.spinnerSublocal1.text = null
            binding.spinnerSublocal2.text = null
            binding.spinnerSublocal3.text = null

            // Esconder sublocais
            binding.tilSublocal1.visibility = View.GONE
            binding.tilSublocal2.visibility = View.GONE
            binding.tilSublocal3.visibility = View.GONE
        }

        // Alterar título se for edição
        (dialog as? AlertDialog)?.setTitle(
            if (clienteExistente != null) "Editar Cliente" else "Novo Cliente"
        )
    }


    private fun setupInputValidation() {
        binding.etNome.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                binding.tilNome.error = if (s.isNullOrBlank()) "Nome é obrigatório" else null
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        binding.etTelefone.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                binding.tilTelefone.error = if (s.isNullOrBlank()) "Telefone é obrigatório" else null
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupSpinners() {
        localAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        sublocal1Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        sublocal2Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        sublocal3Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())

        binding.spinnerLocal.setAdapter(localAdapter)
        binding.spinnerSublocal1.setAdapter(sublocal1Adapter)
        binding.spinnerSublocal2.setAdapter(sublocal2Adapter)
        binding.spinnerSublocal3.setAdapter(sublocal3Adapter)

        // Inicialmente, apenas o spinner de local principal está visível
        binding.tilSublocal1.visibility = View.GONE
        binding.tilSublocal2.visibility = View.GONE
        binding.tilSublocal3.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locais.collect { locais ->
                val rootLocals = locais.filter { it.parentId == null }
                localAdapter.clear()
                localAdapter.addAll(rootLocals)
                localAdapter.notifyDataSetChanged()

                // Reaplica a hierarquia importada, se existir
                importedHierarchy?.let { updateAdaptersWithHierarchy(it) }
            }
        }

        setupSpinnerListeners()
    }

    private fun setupSpinnerListeners() {
        binding.spinnerLocal.setOnItemClickListener { _, _, position, _ ->
            val selectedLocal = localAdapter.getItem(position)
            selectedLocal?.let {
                viewLifecycleOwner.lifecycleScope.launch {
                    val sublocais = viewModel.getSublocais(it.id)
                    updateSublocal1Spinner(sublocais)
                }
            }
        }

        binding.spinnerSublocal1.setOnItemClickListener { _, _, position, _ ->
            val selectedSublocal1 = sublocal1Adapter.getItem(position)
            selectedSublocal1?.let {
                viewLifecycleOwner.lifecycleScope.launch {
                    val sublocais = viewModel.getSublocais(it.id)
                    updateSublocal2Spinner(sublocais)
                }
            }
        }

        binding.spinnerSublocal2.setOnItemClickListener { _, _, position, _ ->
            val selectedSublocal2 = sublocal2Adapter.getItem(position)
            selectedSublocal2?.let {
                viewLifecycleOwner.lifecycleScope.launch {
                    val sublocais = viewModel.getSublocais(it.id)
                    updateSublocal3Spinner(sublocais)
                }
            }
        }
    }

    private fun updateSublocal1Spinner(sublocais: List<Local>) {
        Log.d("NovoClienteDialog", """
        Atualizando Sublocal1Spinner:
        Sublocais: ${sublocais.joinToString { "${it.nome}(${it.id})" }}
        Spinner atual: ${binding.spinnerSublocal1.text}
    """.trimIndent())

        sublocal1Adapter.clear()
        sublocal1Adapter.addAll(sublocais)
        sublocal1Adapter.notifyDataSetChanged()

        // Só exibe se houver sublocais disponíveis
        binding.tilSublocal1.visibility = if (sublocais.isNotEmpty()) View.VISIBLE else View.GONE

        // Se não for uma importação, limpa os níveis subsequentes
        if (importedHierarchy == null) {
            binding.spinnerSublocal1.setText("")
            clearSublocals(2)
        }
    }

    private fun updateSublocal2Spinner(sublocais: List<Local>) {
        Log.d("NovoClienteDialog", """
        Atualizando Sublocal2Spinner:
        Sublocais: ${sublocais.joinToString { "${it.nome}(${it.id})" }}
        Spinner atual: ${binding.spinnerSublocal2.text}
    """.trimIndent())

        sublocal2Adapter.clear()
        sublocal2Adapter.addAll(sublocais)
        sublocal2Adapter.notifyDataSetChanged()

        // Só exibe se houver sublocais disponíveis e o sublocal1 estiver preenchido
        binding.tilSublocal2.visibility = if (sublocais.isNotEmpty() &&
            !binding.spinnerSublocal1.text.isNullOrBlank())
            View.VISIBLE else View.GONE

        // Se não for uma importação, limpa os níveis subsequentes
        if (importedHierarchy == null) {
            binding.spinnerSublocal2.setText("")
            clearSublocals(3)
        }
    }

    private fun updateSublocal3Spinner(sublocais: List<Local>) {
        Log.d("NovoClienteDialog", """
        Atualizando Sublocal3Spinner:
        Sublocais: ${sublocais.joinToString { "${it.nome}(${it.id})" }}
        Spinner atual: ${binding.spinnerSublocal3.text}
    """.trimIndent())

        sublocal3Adapter.clear()
        sublocal3Adapter.addAll(sublocais)
        sublocal3Adapter.notifyDataSetChanged()

        // Só exibe se houver sublocais disponíveis e o sublocal2 estiver preenchido
        binding.tilSublocal3.visibility = if (sublocais.isNotEmpty() &&
            !binding.spinnerSublocal2.text.isNullOrBlank())
            View.VISIBLE else View.GONE

        // Se não for uma importação, limpa o campo
        if (importedHierarchy == null) {
            binding.spinnerSublocal3.setText("")
        }
    }

    private fun clearSublocals(startLevel: Int = 1) {
        when (startLevel) {
            1 -> {
                binding.spinnerSublocal1.setText("")
                binding.tilSublocal1.visibility = View.GONE
                clearSublocals(2)
            }
            2 -> {
                binding.spinnerSublocal2.setText("")
                binding.tilSublocal2.visibility = View.GONE
                clearSublocals(3)
            }
            3 -> {
                binding.spinnerSublocal3.setText("")
                binding.tilSublocal3.visibility = View.GONE
            }
        }
    }

    private fun updateAdaptersWithHierarchy(hierarchy: List<Local>) {
        viewLifecycleOwner.lifecycleScope.launch {
            hierarchy.forEachIndexed { index, local ->
                when (index) {
                    0 -> {
                        binding.spinnerLocal.setText(local.nome)
                    }
                    1 -> {
                        val sublocais = viewModel.getSublocais(hierarchy[0].id)
                        sublocal1Adapter.clear()
                        sublocal1Adapter.addAll(sublocais)
                        binding.spinnerSublocal1.setText(local.nome)
                        // Exibe apenas se o local principal estiver preenchido
                        binding.tilSublocal1.visibility = if (!binding.spinnerLocal.text.isNullOrBlank())
                            View.VISIBLE else View.GONE
                    }
                    2 -> {
                        val sublocais = viewModel.getSublocais(hierarchy[1].id)
                        sublocal2Adapter.clear()
                        sublocal2Adapter.addAll(sublocais)
                        binding.spinnerSublocal2.setText(local.nome)
                        // Exibe apenas se o sublocal1 estiver preenchido
                        binding.tilSublocal2.visibility = if (!binding.spinnerSublocal1.text.isNullOrBlank())
                            View.VISIBLE else View.GONE
                    }
                    3 -> {
                        val sublocais = viewModel.getSublocais(hierarchy[2].id)
                        sublocal3Adapter.clear()
                        sublocal3Adapter.addAll(sublocais)
                        binding.spinnerSublocal3.setText(local.nome)
                        // Exibe apenas se o sublocal2 estiver preenchido
                        binding.tilSublocal3.visibility = if (!binding.spinnerSublocal2.text.isNullOrBlank())
                            View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun setupDialogButtons() {
        (dialog as? AlertDialog)?.setOnShowListener { dialog ->
            (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateInputs()) {
                    val nome = binding.etNome.text.toString()
                    val telefone = binding.etTelefone.text.toString()

                    val localId = importedHierarchy?.firstOrNull()?.id
                        ?: binding.spinnerLocal.text.toString().let { text ->
                            localAdapter.getAllItems().find { it.nome == text }?.id
                        } ?: return@setOnClickListener

                    val sublocal1Id = importedHierarchy?.getOrNull(1)?.id
                        ?: (if (binding.tilSublocal1.visibility == View.VISIBLE) {
                            binding.spinnerSublocal1.text.toString().let { text ->
                                sublocal1Adapter.getAllItems().find { it.nome == text }?.id
                            }
                        } else null)

                    val sublocal2Id = importedHierarchy?.getOrNull(2)?.id
                        ?: (if (binding.tilSublocal2.visibility == View.VISIBLE) {
                            binding.spinnerSublocal2.text.toString().let { text ->
                                sublocal2Adapter.getAllItems().find { it.nome == text }?.id
                            }
                        } else null)

                    val sublocal3Id = importedHierarchy?.getOrNull(3)?.id
                        ?: (if (binding.tilSublocal3.visibility == View.VISIBLE) {
                            binding.spinnerSublocal3.text.toString().let { text ->
                                sublocal3Adapter.getAllItems().find { it.nome == text }?.id
                            }
                        } else null)

                    Log.d("NovoClienteDialog", """
                        Valores sendo enviados para cadastro:
                        Nome: $nome
                        Local ID: $localId
                        Sublocal 1 ID: $sublocal1Id
                        Sublocal 2 ID: $sublocal2Id
                        Sublocal 3 ID: $sublocal3Id
                    """.trimIndent())

                    onClienteAdicionado?.invoke(nome, telefone, localId, sublocal1Id, sublocal2Id, sublocal3Id)
                    dismiss()
                }
            }
        }
    }

    private fun setupImportButton() {
        binding.btnImportar.setOnClickListener {
            viewModel.localSelecionado.value?.let { selectedLocal ->
                Log.d("NovoClienteDialog", "Importando local: ${selectedLocal.nome} (ID: ${selectedLocal.id})")
                importLocalHierarchy(selectedLocal)
            }
        }
    }

    private fun importLocalHierarchy(selectedLocal: Local) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val hierarchy = viewModel.getLocalHierarchy(selectedLocal.id)
                Log.d("NovoClienteDialog", "Hierarquia obtida: ${hierarchy.joinToString(" -> ") { "${it.nome}(${it.id})" }}")

                if (hierarchy.isNotEmpty()) {
                    importedHierarchy = hierarchy
                    updateAdaptersWithHierarchy(hierarchy)
                }
            } catch (e: Exception) {
                Log.e("NovoClienteDialog", "Erro ao importar hierarquia", e)
                Toast.makeText(context, "Erro ao importar local: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (binding.etNome.text.isNullOrBlank()) {
            binding.tilNome.error = "Nome é obrigatório"
            isValid = false
        }

        if (binding.etTelefone.text.isNullOrBlank()) {
            binding.tilTelefone.error = "Telefone é obrigatório"
            isValid = false
        }

        if (binding.spinnerLocal.text.isNullOrBlank()) {
            binding.tilLocal.error = "Selecione um local"
            isValid = false
        }

        return isValid
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun <T> ArrayAdapter<T>.getAllItems(): List<T> {
        return (0 until count).map { getItem(it) }.filterNotNull()
    }
}