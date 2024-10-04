package com.example.caderneta.ui.vendas

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.caderneta.data.entity.Local
import com.example.caderneta.databinding.DialogNovoClienteBinding
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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogNovoClienteBinding.inflate(LayoutInflater.from(context))

        return AlertDialog.Builder(requireContext())
            .setTitle("Adicionar Novo Cliente")
            .setView(binding.root)
            .setPositiveButton("Adicionar", null)
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
        localAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf<Local>())
        sublocal1Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf<Local>())
        sublocal2Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf<Local>())
        sublocal3Adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf<Local>())

        binding.spinnerLocal.setAdapter(localAdapter)
        binding.spinnerSublocal1.setAdapter(sublocal1Adapter)
        binding.spinnerSublocal2.setAdapter(sublocal2Adapter)
        binding.spinnerSublocal3.setAdapter(sublocal3Adapter)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.locais.collect { locais ->
                updateLocalSpinner(locais)
            }
        }

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

    private fun updateLocalSpinner(locais: List<Local>) {
        val rootLocals = locais.filter { it.parentId == null }
        localAdapter.clear()
        localAdapter.addAll(rootLocals)
        localAdapter.notifyDataSetChanged()
        clearSublocals()
    }

    private fun updateSublocals(local: Local) {
        viewLifecycleOwner.lifecycleScope.launch {
            val sublocais = viewModel.getSublocais(local.id)
            updateSublocal1Spinner(sublocais)
        }
    }

    private fun updateSublocal1Spinner(sublocais: List<Local>) {
        sublocal1Adapter.clear()
        sublocal1Adapter.addAll(sublocais)
        sublocal1Adapter.notifyDataSetChanged()
        binding.tilSublocal1.visibility = if (sublocais.isNotEmpty()) View.VISIBLE else View.GONE
        binding.spinnerSublocal1.setText("") // Limpa a seleção anterior
        clearSublocals(2)
    }

    private fun updateSublocal2Spinner(sublocais: List<Local>) {
        sublocal2Adapter.clear()
        sublocal2Adapter.addAll(sublocais)
        sublocal2Adapter.notifyDataSetChanged()
        binding.tilSublocal2.visibility = if (sublocais.isNotEmpty()) View.VISIBLE else View.GONE
        binding.spinnerSublocal2.setText("") // Limpa a seleção anterior
        clearSublocals(3)
    }

    private fun updateSublocal3Spinner(sublocais: List<Local>) {
        sublocal3Adapter.clear()
        sublocal3Adapter.addAll(sublocais)
        sublocal3Adapter.notifyDataSetChanged()
        binding.tilSublocal3.visibility = if (sublocais.isNotEmpty()) View.VISIBLE else View.GONE
        binding.spinnerSublocal3.setText("") // Limpa a seleção anterior
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

    private fun setupDialogButtons() {
        (dialog as? AlertDialog)?.setOnShowListener { dialog ->
            (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (validateInputs()) {
                    val nome = binding.etNome.text.toString()
                    val telefone = binding.etTelefone.text.toString()

                    val localId = binding.spinnerLocal.text.toString().let { text ->
                        localAdapter.getAllItems().find { it.nome == text }?.id
                    } ?: return@setOnClickListener

                    val sublocal1Id = binding.spinnerSublocal1.text.toString().let { text ->
                        sublocal1Adapter.getAllItems().find { it.nome == text }?.id
                    }
                    val sublocal2Id = binding.spinnerSublocal2.text.toString().let { text ->
                        sublocal2Adapter.getAllItems().find { it.nome == text }?.id
                    }
                    val sublocal3Id = binding.spinnerSublocal3.text.toString().let { text ->
                        sublocal3Adapter.getAllItems().find { it.nome == text }?.id
                    }

                    onClienteAdicionado?.invoke(nome, telefone, localId, sublocal1Id, sublocal2Id, sublocal3Id)
                    dismiss()
                }
            }
        }
    }

    private fun setupImportButton() {
        binding.btnImportar.setOnClickListener {
            viewModel.localSelecionado.value?.let { selectedLocal ->
                importLocalHierarchy(selectedLocal)
            }
        }
    }

    private fun importLocalHierarchy(selectedLocal: Local) {
        viewLifecycleOwner.lifecycleScope.launch {
            val hierarchy = viewModel.getLocalHierarchy(selectedLocal.id)
            if (hierarchy.isNotEmpty()) {
                updateLocalSpinnerWithHierarchy(hierarchy)
            }
        }
    }

    private fun updateLocalSpinnerWithHierarchy(hierarchy: List<Local>) {
        binding.spinnerLocal.setText("")
        binding.spinnerSublocal1.setText("")
        binding.spinnerSublocal2.setText("")
        binding.spinnerSublocal3.setText("")

        hierarchy.forEach { local ->
            when (local.level) {
                0 -> binding.spinnerLocal.setText(local.nome)
                1 -> binding.spinnerSublocal1.setText(local.nome)
                2 -> binding.spinnerSublocal2.setText(local.nome)
                3 -> binding.spinnerSublocal3.setText(local.nome)
            }
        }

        binding.tilSublocal1.visibility = if (hierarchy.any { it.level == 1 }) View.VISIBLE else View.GONE
        binding.tilSublocal2.visibility = if (hierarchy.any { it.level == 2 }) View.VISIBLE else View.GONE
        binding.tilSublocal3.visibility = if (hierarchy.any { it.level == 3 }) View.VISIBLE else View.VISIBLE
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

    // Extensão para ArrayAdapter
    private fun <T> ArrayAdapter<T>.getAllItems(): List<T> {
        return (0 until count).map { getItem(it) }.filterNotNull()
    }
}