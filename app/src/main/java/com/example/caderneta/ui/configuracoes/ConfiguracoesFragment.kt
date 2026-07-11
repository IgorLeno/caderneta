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
import com.example.caderneta.BuildConfig
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.databinding.FragmentConfiguracoesBinding
import com.example.caderneta.domain.configuracoes.ConfiguracoesCampo
import com.example.caderneta.domain.configuracoes.ConfiguracoesFormResult
import com.example.caderneta.domain.configuracoes.ConfiguracoesFormValidator
import com.example.caderneta.domain.configuracoes.ConfiguracoesInput
import com.example.caderneta.util.centavosParaTextoDecimal
import com.example.caderneta.viewmodel.ConfiguracoesViewModel
import com.example.caderneta.viewmodel.ConfiguracoesViewModelFactory
import com.example.caderneta.viewmodel.UiEvento
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Suppress("TooManyFunctions")
class ConfiguracoesFragment : Fragment() {
    private var _binding: FragmentConfiguracoesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ConfiguracoesViewModel by viewModels {
        val app = requireActivity().application as CadernetaApplication
        ConfiguracoesViewModelFactory(
            app.container.configuracoesRepository,
            app.container.backupManager,
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
                binding.tvPromocoesStatus.text =
                    if (ativadas) {
                        "Promoções ativadas"
                    } else {
                        "Promoções desativadas. Os dados cadastrados ficam preservados."
                    }
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
                        is UiEvento.Erro ->
                            Snackbar.make(binding.root, evento.mensagem, Snackbar.LENGTH_LONG).show()
                        is UiEvento.Sucesso ->
                            Snackbar.make(binding.root, evento.mensagem, Snackbar.LENGTH_SHORT).show()
                        is UiEvento.ConfirmarRestauracao -> confirmarRestauracao(evento.resumo)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.salvando.collectLatest { salvando ->
                binding.btnSalvarConfiguracoes.isEnabled = !salvando
            }
        }

        binding.tvBuildInfo.text = buildInfo()
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

    private fun lerConfiguracoesOuFocarErro(): Configuracoes? {
        limparErrosCampos()
        return when (val resultado = ConfiguracoesFormValidator.validar(binding.toConfiguracoesInput())) {
            is ConfiguracoesFormResult.Valida -> resultado.configuracoes
            is ConfiguracoesFormResult.Invalida -> {
                mostrarErros(resultado.erros)
                null
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

    private fun limparErrosCampos() {
        campoViews().values.forEach { it.error = null }
    }

    private fun mostrarErros(erros: Map<ConfiguracoesCampo, String>) {
        val views = campoViews()
        erros.forEach { (campo, mensagem) -> views[campo]?.error = mensagem }
        erros.keys.firstOrNull()?.let { views[it]?.requestFocus() }
    }

    private fun campoViews(): Map<ConfiguracoesCampo, EditText> =
        binding.run {
            mapOf(
                ConfiguracoesCampo.SALGADO_VISTA to etSalgadoVista,
                ConfiguracoesCampo.SALGADO_PRAZO to etSalgadoPrazo,
                ConfiguracoesCampo.SUCO_VISTA to etSucoVista,
                ConfiguracoesCampo.SUCO_PRAZO to etSucoPrazo,
                ConfiguracoesCampo.PROMO1_NOME to etPromo1Nome,
                ConfiguracoesCampo.PROMO1_SALGADOS to etPromo1Salgados,
                ConfiguracoesCampo.PROMO1_SUCOS to etPromo1Sucos,
                ConfiguracoesCampo.PROMO1_VISTA to etPromo1Vista,
                ConfiguracoesCampo.PROMO1_PRAZO to etPromo1Prazo,
                ConfiguracoesCampo.PROMO2_NOME to etPromo2Nome,
                ConfiguracoesCampo.PROMO2_SALGADOS to etPromo2Salgados,
                ConfiguracoesCampo.PROMO2_SUCOS to etPromo2Sucos,
                ConfiguracoesCampo.PROMO2_VISTA to etPromo2Vista,
                ConfiguracoesCampo.PROMO2_PRAZO to etPromo2Prazo,
            )
        }

    private fun FragmentConfiguracoesBinding.toConfiguracoesInput(): ConfiguracoesInput =
        ConfiguracoesInput(
            precoSalgadoVista = etSalgadoVista.text.toString(),
            precoSalgadoPrazo = etSalgadoPrazo.text.toString(),
            precoSucoVista = etSucoVista.text.toString(),
            precoSucoPrazo = etSucoPrazo.text.toString(),
            promocoesAtivadas = switchPromocoes.isChecked,
            promo1Nome = etPromo1Nome.text.toString(),
            promo1Salgados = etPromo1Salgados.text.toString(),
            promo1Sucos = etPromo1Sucos.text.toString(),
            promo1Vista = etPromo1Vista.text.toString(),
            promo1Prazo = etPromo1Prazo.text.toString(),
            promo2Nome = etPromo2Nome.text.toString(),
            promo2Salgados = etPromo2Salgados.text.toString(),
            promo2Sucos = etPromo2Sucos.text.toString(),
            promo2Vista = etPromo2Vista.text.toString(),
            promo2Prazo = etPromo2Prazo.text.toString(),
        )

    private fun buildInfo(): String =
        auditWarning() +
            "Caderneta ${BuildConfig.VERSION_NAME}\n" +
            "Código ${BuildConfig.VERSION_CODE}\n" +
            "Build ${BuildConfig.BUILD_TYPE}\n" +
            "Audit ${BuildConfig.IS_AUDIT}\n" +
            "Dirty ${BuildConfig.GIT_DIRTY}\n" +
            "Commit ${BuildConfig.GIT_SHA}\n" +
            "Build ${BuildConfig.BUILD_TIME}\n" +
            "Banco ${BuildConfig.DB_VERSION}"

    private fun auditWarning(): String =
        if (BuildConfig.IS_AUDIT) {
            "AUDITORIA - dados ficticios\n"
        } else {
            ""
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
