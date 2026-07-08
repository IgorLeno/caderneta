package com.example.caderneta.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.util.rethrowCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ConfiguracoesViewModel(
    private val repository: ConfiguracoesRepository,
) : ViewModel() {
    /** Nulo enquanto não houver configuração salva (primeira execução). */
    private val _configuracoes = MutableStateFlow<Configuracoes?>(null)
    val configuracoes: StateFlow<Configuracoes?> = _configuracoes

    private val _promocoesAtivadas = MutableStateFlow(false)
    val promocoesAtivadas: StateFlow<Boolean> = _promocoesAtivadas

    private val _eventos = Channel<UiEvento>(Channel.BUFFERED)
    val eventos = _eventos.receiveAsFlow()

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
        viewModelScope.launch {
            try {
                if (novasConfiguracoes.isValid()) {
                    repository.salvarConfiguracoes(novasConfiguracoes)
                    _eventos.send(UiEvento.Sucesso("Configurações salvas"))
                } else {
                    _eventos.send(UiEvento.Erro("Configurações inválidas. Verifique os valores inseridos."))
                }
            } catch (e: Exception) {
                e.rethrowCancellation()
                _eventos.send(UiEvento.Erro("Erro ao salvar configurações: ${e.message}"))
            }
        }
    }
}

class ConfiguracoesViewModelFactory(
    private val repository: ConfiguracoesRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConfiguracoesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConfiguracoesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
