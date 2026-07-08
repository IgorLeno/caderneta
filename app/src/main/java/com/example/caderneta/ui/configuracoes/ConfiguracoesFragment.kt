package com.example.caderneta.ui.configuracoes

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.databinding.FragmentConfiguracoesBinding
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.util.ParseDinheiro
import com.example.caderneta.util.centavosParaTextoDecimal
import com.example.caderneta.util.parseDinheiro
import com.example.caderneta.viewmodel.ConfiguracoesViewModel
import com.example.caderneta.viewmodel.ConfiguracoesViewModelFactory
import com.example.caderneta.viewmodel.UiEvento
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ConfiguracoesFragment : Fragment() {
    private var _binding: FragmentConfiguracoesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConfiguracoesViewModel by viewModels {
        val app = requireActivity().application as CadernetaApplication
        ConfiguracoesViewModelFactory(
            ConfiguracoesRepository(
                app.database.configuracoesDao(),
            ),
            app.backupManager,
        )
    }

    private val criarBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { viewModel.exportarBackup(it) }
        }

    private val abrirBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.prepararRestauracao(it) }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentConfiguracoesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupObservers()
        setupListeners()
    }

    private fun setupObservers() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.configuracoes.collectLatest { configuracoes ->
                configuracoes?.let { updateUI(it) }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.promocoesAtivadas.collectLatest { ativadas ->
                binding.tlPromocoes.visibility = if (ativadas) View.VISIBLE else View.GONE
                binding.tvPromocoesStatus.text = if (ativadas) "Promoções ativadas" else "Promoções desativadas"
                if (!ativadas) limparErrosPromocoes()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ultimoBackupMillis.collectLatest { millis ->
                binding.tvUltimoBackup.text =
                    millis?.let { "Último backup: ${DateFormat.getDateTimeInstance().format(Date(it))}" }
                        ?: "Último backup: nunca"
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.eventos.collectLatest { evento ->
                    when (evento) {
                        is UiEvento.Erro -> Snackbar.make(binding.root, evento.mensagem, Snackbar.LENGTH_LONG).show()
                        is UiEvento.Sucesso -> Snackbar.make(binding.root, evento.mensagem, Snackbar.LENGTH_SHORT).show()
                        is UiEvento.ConfirmarRestauracao -> confirmarRestauracao(evento.resumo)
                    }
                }
            }
        }
    }

    private fun updateUI(configuracoes: Configuracoes) {
        binding.apply {
            etSalgadoVista.setText(configuracoes.precoSalgadoVistaCentavos.centavosParaTextoDecimal())
            etSalgadoPrazo.setText(configuracoes.precoSalgadoPrazoCentavos.centavosParaTextoDecimal())
            etSucoVista.setText(configuracoes.precoSucoVistaCentavos.centavosParaTextoDecimal())
            etSucoPrazo.setText(configuracoes.precoSucoPrazoCentavos.centavosParaTextoDecimal())
            switchPromocoes.isChecked = configuracoes.promocoesAtivadas
            etPromo1Nome.setText(configuracoes.promo1Nome)
            etPromo1Salgados.setText(configuracoes.promo1Salgados.toString())
            etPromo1Sucos.setText(configuracoes.promo1Sucos.toString())
            etPromo1Vista.setText(configuracoes.promo1VistaCentavos.centavosParaTextoDecimal())
            etPromo1Prazo.setText(configuracoes.promo1PrazoCentavos.centavosParaTextoDecimal())
            etPromo2Nome.setText(configuracoes.promo2Nome)
            etPromo2Salgados.setText(configuracoes.promo2Salgados.toString())
            etPromo2Sucos.setText(configuracoes.promo2Sucos.toString())
            etPromo2Vista.setText(configuracoes.promo2VistaCentavos.centavosParaTextoDecimal())
            etPromo2Prazo.setText(configuracoes.promo2PrazoCentavos.centavosParaTextoDecimal())
        }
    }

    private fun setupListeners() {
        binding.switchPromocoes.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setPromocoesAtivadas(isChecked)
        }

        binding.btnSalvarConfiguracoes.setOnClickListener {
            val novasConfiguracoes = lerConfiguracoesOuFocarErro() ?: return@setOnClickListener
            viewModel.salvarConfiguracoes(novasConfiguracoes)
        }

        binding.btnExportarBackup.setOnClickListener {
            criarBackupLauncher.launch(nomeArquivoBackup())
        }

        binding.btnRestaurarBackup.setOnClickListener {
            abrirBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
        }
    }

    private fun confirmarRestauracao(resumo: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Restaurar backup?")
            .setMessage(resumo)
            .setPositiveButton("Restaurar") { _, _ -> viewModel.confirmarRestauracao() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun nomeArquivoBackup(): String {
        val data = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        return "caderneta-backup-$data.json"
    }

    @Suppress("LongMethod")
    private fun lerConfiguracoesOuFocarErro(): Configuracoes? {
        var primeiroErro: EditText? = null

        fun <T> validar(
            campo: EditText,
            leitura: () -> T?,
        ): T? {
            val valor = leitura()
            if (valor == null && primeiroErro == null) primeiroErro = campo
            return valor
        }

        binding.apply {
            val salgadoVista = validar(etSalgadoVista) { etSalgadoVista.lerCentavosOuErro("Informe o preço à vista") }
            val salgadoPrazo = validar(etSalgadoPrazo) { etSalgadoPrazo.lerCentavosOuErro("Informe o preço a prazo") }
            val sucoVista = validar(etSucoVista) { etSucoVista.lerCentavosOuErro("Informe o preço à vista") }
            val sucoPrazo = validar(etSucoPrazo) { etSucoPrazo.lerCentavosOuErro("Informe o preço a prazo") }
            val promocoesAtivadas = switchPromocoes.isChecked

            val promo1Nome = etPromo1Nome.text.toString().trim()
            val promo2Nome = etPromo2Nome.text.toString().trim()

            if (promocoesAtivadas && promo1Nome.isBlank()) {
                etPromo1Nome.error = "Informe o nome da promoção"
                if (primeiroErro == null) primeiroErro = etPromo1Nome
            } else {
                etPromo1Nome.error = null
            }

            if (promocoesAtivadas && promo2Nome.isBlank()) {
                etPromo2Nome.error = "Informe o nome da promoção"
                if (primeiroErro == null) primeiroErro = etPromo2Nome
            } else {
                etPromo2Nome.error = null
            }

            val promo1Salgados = validar(etPromo1Salgados) { etPromo1Salgados.lerInteiroOuErro(promocoesAtivadas) }
            val promo1Sucos = validar(etPromo1Sucos) { etPromo1Sucos.lerInteiroOuErro(promocoesAtivadas) }
            val promo1Vista = validar(etPromo1Vista) { etPromo1Vista.lerCentavosOuErro("Informe o valor à vista", promocoesAtivadas) }
            val promo1Prazo = validar(etPromo1Prazo) { etPromo1Prazo.lerCentavosOuErro("Informe o valor a prazo", promocoesAtivadas) }
            val promo2Salgados = validar(etPromo2Salgados) { etPromo2Salgados.lerInteiroOuErro(promocoesAtivadas) }
            val promo2Sucos = validar(etPromo2Sucos) { etPromo2Sucos.lerInteiroOuErro(promocoesAtivadas) }
            val promo2Vista = validar(etPromo2Vista) { etPromo2Vista.lerCentavosOuErro("Informe o valor à vista", promocoesAtivadas) }
            val promo2Prazo = validar(etPromo2Prazo) { etPromo2Prazo.lerCentavosOuErro("Informe o valor a prazo", promocoesAtivadas) }

            primeiroErro?.let {
                it.requestFocus()
                return null
            }

            return Configuracoes(
                precoSalgadoVistaCentavos = requireNotNull(salgadoVista),
                precoSalgadoPrazoCentavos = requireNotNull(salgadoPrazo),
                precoSucoVistaCentavos = requireNotNull(sucoVista),
                precoSucoPrazoCentavos = requireNotNull(sucoPrazo),
                promocoesAtivadas = promocoesAtivadas,
                promo1Nome = promo1Nome,
                promo1Salgados = promo1Salgados ?: 0,
                promo1Sucos = promo1Sucos ?: 0,
                promo1VistaCentavos = promo1Vista ?: 0,
                promo1PrazoCentavos = promo1Prazo ?: 0,
                promo2Nome = promo2Nome,
                promo2Salgados = promo2Salgados ?: 0,
                promo2Sucos = promo2Sucos ?: 0,
                promo2VistaCentavos = promo2Vista ?: 0,
                promo2PrazoCentavos = promo2Prazo ?: 0,
            )
        }
    }

    private fun EditText.lerCentavosOuErro(
        mensagemVazio: String,
        obrigatorio: Boolean = true,
    ): Long? {
        val texto = text.toString()
        if (!obrigatorio && texto.isBlank()) {
            error = null
            return null
        }

        return when (val resultado = texto.parseDinheiro()) {
            is ParseDinheiro.Valido -> {
                if (resultado.centavos > 0) {
                    error = null
                    resultado.centavos
                } else {
                    error = "Informe um valor maior que zero"
                    null
                }
            }
            ParseDinheiro.Vazio -> {
                error = mensagemVazio
                null
            }
            ParseDinheiro.Invalido -> {
                error = "Valor inválido"
                null
            }
            ParseDinheiro.Negativo -> {
                error = "Valor não pode ser negativo"
                null
            }
            ParseDinheiro.MuitoGrande -> {
                error = "Valor muito grande"
                null
            }
        }
    }

    private fun EditText.lerInteiroOuErro(obrigatorio: Boolean): Int? {
        val texto = text.toString().trim()
        if (!obrigatorio && texto.isBlank()) {
            error = null
            return null
        }

        val valor = texto.toIntOrNull()
        return when {
            texto.isBlank() -> {
                error = "Informe a quantidade"
                null
            }
            valor == null -> {
                error = "Quantidade inválida"
                null
            }
            valor < 0 -> {
                error = "Quantidade não pode ser negativa"
                null
            }
            else -> {
                error = null
                valor
            }
        }
    }

    private fun limparErrosPromocoes() {
        binding.apply {
            listOf(
                etPromo1Nome,
                etPromo1Salgados,
                etPromo1Sucos,
                etPromo1Vista,
                etPromo1Prazo,
                etPromo2Nome,
                etPromo2Salgados,
                etPromo2Sucos,
                etPromo2Vista,
                etPromo2Prazo,
            ).forEach { it.error = null }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
