package com.example.caderneta.ui.configuracoes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.R
import com.example.caderneta.data.entity.TipoProduto
import com.example.caderneta.databinding.FragmentConfiguracoesBinding
import com.example.caderneta.viewmodel.ConfiguracoesViewModel
import com.example.caderneta.viewmodel.ConfiguracoesViewModelFactory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConfiguracoesFragment : Fragment() {

    private var _binding: FragmentConfiguracoesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConfiguracoesViewModel by viewModels {
        ConfiguracoesViewModelFactory(
            (requireActivity().application as CadernetaApplication).produtoRepository
        )
    }

    private lateinit var produtosAdapter: ProdutosAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConfiguracoesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupAddButton()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        produtosAdapter = ProdutosAdapter(
            onPrecoChanged = { produtoId, novoPreco -> viewModel.atualizarPrecoProduto(produtoId, novoPreco) },
            onRemoveClick = { produtoId -> confirmarRemocaoProduto(produtoId) }
        )
        binding.rvProdutos.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = produtosAdapter
        }
    }

    private fun setupAddButton() {
        binding.btnAdicionarProduto.setOnClickListener {
            showAdicionarProdutoDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collectLatest { isLoading ->
                binding.progressBar.isVisible = isLoading
                binding.rvProdutos.isVisible = !isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collectLatest { errorMessage ->
                errorMessage?.let {
                    Snackbar.make(binding.root, it, Snackbar.LENGTH_LONG).show()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.produtos.collectLatest { produtos ->
                produtosAdapter.submitList(produtos)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.precoAtualizado.collectLatest { produtoAtualizado ->
                produtoAtualizado?.let {
                    Snackbar.make(binding.root, "Preço atualizado com sucesso", Snackbar.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun confirmarRemocaoProduto(produtoId: Long) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Remover Produto")
            .setMessage("Tem certeza que deseja remover este produto?")
            .setPositiveButton("Sim") { _, _ ->
                viewModel.removerProduto(produtoId)
            }
            .setNegativeButton("Não", null)
            .show()
    }

    private fun showAdicionarProdutoDialog() {
        val dialog = AdicionarProdutoDialog()
        dialog.onProdutoAdicionado = { nome, preco, tipo ->
            viewModel.adicionarNovoProduto(nome, preco, tipo)
        }
        dialog.show(childFragmentManager, "AdicionarProdutoDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}