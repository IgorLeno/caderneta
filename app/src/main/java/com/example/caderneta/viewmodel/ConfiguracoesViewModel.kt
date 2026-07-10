package com.example.caderneta.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.backup.BackupManager
import com.example.caderneta.data.backup.BackupSnapshot
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.util.EspressoIdlingResource
import com.example.caderneta.util.rethrowCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ConfiguracoesViewModel(
    private val repository: ConfiguracoesRepository,
    private val backupManager: BackupManager,
) : ViewModel() {
    /** Nulo enquanto não houver configuração salva (primeira execução). */
    private val _configuracoes = MutableStateFlow<Configuracoes?>(null)
    val configuracoes: StateFlow<Configuracoes?> = _configuracoes

    private val _promocoesAtivadas = MutableStateFlow(false)
    val promocoesAtivadas: StateFlow<Boolean> = _promocoesAtivadas

    private val _ultimoBackupMillis = MutableStateFlow(backupManager.getUltimoBackupMillis())
    val ultimoBackupMillis: StateFlow<Long?> = _ultimoBackupMillis

    private val _salvando = MutableStateFlow(false)
    val salvando: StateFlow<Boolean> = _salvando.asStateFlow()

    private val _eventos = Channel<UiEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

    private var restauracaoPendente: BackupSnapshot? = null

    init {
        loadConfiguracoes()
    }

    private fun loadConfiguracoes() {
        viewModelScope.launch {
            repository
                .getConfiguracoes()
                .catch { e ->
                    e.rethrowCancellation()
                    _eventos.send(UiEvento.Erro("Erro ao carregar configurações: ${e.message}"))
                }.collect { configuracoes ->
                    _configuracoes.value = configuracoes
                    _promocoesAtivadas.value = configuracoes?.promocoesAtivadas ?: false
                }
        }
    }

    fun setPromocoesAtivadas(ativadas: Boolean) {
        _promocoesAtivadas.value = ativadas
    }

    fun salvarConfiguracoes(novasConfiguracoes: Configuracoes) {
        if (_salvando.value) return
        EspressoIdlingResource.increment()
        viewModelScope.launch {
            try {
                _salvando.value = true
                if (novasConfiguracoes.isValid()) {
                    repository.salvarConfiguracoes(novasConfiguracoes)
                    val persistida = repository.getConfiguracoesOnce()
                    if (persistida != novasConfiguracoes.copy(id = 1)) {
                        _eventos.send(UiEvento.Erro("Erro ao confirmar configurações salvas"))
                        return@launch
                    }
                    _eventos.send(UiEvento.Sucesso("Configurações salvas"))
                } else {
                    _eventos.send(UiEvento.Erro("Configurações inválidas. Verifique os valores inseridos."))
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                _eventos.send(UiEvento.Erro("Erro ao salvar configurações: ${e.message}"))
            } finally {
                _salvando.value = false
                EspressoIdlingResource.decrement()
            }
        }
    }

    fun exportarBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                backupManager.exportar(uri)
                _ultimoBackupMillis.value = backupManager.getUltimoBackupMillis()
                _eventos.send(UiEvento.Sucesso("Backup exportado"))
            } catch (e: Exception) {
                e.rethrowCancellation()
                _eventos.send(UiEvento.Erro("Erro ao exportar backup: ${e.message}"))
            }
        }
    }

    fun prepararRestauracao(uri: Uri) {
        viewModelScope.launch {
            try {
                val (snapshot, resumo) = backupManager.lerResumo(uri)
                restauracaoPendente = snapshot
                _eventos.send(
                    UiEvento.ConfirmarRestauracao(
                        "${resumo.clientes} clientes, ${resumo.lancamentos} lançamentos. Isso apagará os dados atuais.",
                    ),
                )
            } catch (e: Exception) {
                e.rethrowCancellation()
                _eventos.send(UiEvento.Erro("Backup inválido: ${e.message}"))
            }
        }
    }

    fun confirmarRestauracao() {
        val snapshot = restauracaoPendente ?: return
        viewModelScope.launch {
            try {
                backupManager.restaurar(snapshot)
                restauracaoPendente = null
                _eventos.send(UiEvento.Sucesso("Backup restaurado"))
            } catch (e: Exception) {
                e.rethrowCancellation()
                _eventos.send(UiEvento.Erro("Erro ao restaurar backup: ${e.message}"))
            }
        }
    }
}

class ConfiguracoesViewModelFactory(
    private val repository: ConfiguracoesRepository,
    private val backupManager: BackupManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConfiguracoesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConfiguracoesViewModel(repository, backupManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
