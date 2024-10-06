package com.example.caderneta.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.repository.ConfiguracoesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class ConfiguracoesViewModel(private val repository: ConfiguracoesRepository) : ViewModel() {

    private val _configuracoes = MutableStateFlow<Configuracoes?>(null)
    val configuracoes: StateFlow<Configuracoes?> = _configuracoes

    private val _promocoesAtivadas = MutableStateFlow(false)
    val promocoesAtivadas: StateFlow<Boolean> = _promocoesAtivadas

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        loadConfiguracoes()
    }

    private fun loadConfiguracoes() {
        viewModelScope.launch {
            repository.getConfiguracoes()
                .catch { e ->
                    _error.value = "Erro ao carregar configurações: ${e.message}"
                }
                .collect { configuracoes ->
                    _configuracoes.value = configuracoes
                    _promocoesAtivadas.value = configuracoes.promocoesAtivadas
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
                    _configuracoes.value = novasConfiguracoes
                    _error.value = null
                } else {
                    _error.value = "Configurações inválidas. Verifique os valores inseridos."
                }
            } catch (e: Exception) {
                _error.value = "Erro ao salvar configurações: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}

class ConfiguracoesViewModelFactory(private val repository: ConfiguracoesRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ConfiguracoesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ConfiguracoesViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}