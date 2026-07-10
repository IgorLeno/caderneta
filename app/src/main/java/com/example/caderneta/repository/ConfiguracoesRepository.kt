package com.example.caderneta.repository

import com.example.caderneta.data.dao.ConfiguracoesDao
import com.example.caderneta.data.entity.Configuracoes
import kotlinx.coroutines.flow.Flow

class ConfiguracoesRepository(
    private val configuracoesDao: ConfiguracoesDao,
) {
    /**
     * Nulo enquanto o vendedor não configurou os preços (primeira execução).
     * Quem consome decide bloquear os fluxos que dependem de preço.
     */
    fun getConfiguracoes(): Flow<Configuracoes?> = configuracoesDao.getConfiguracoes()

    suspend fun getConfiguracoesOnce(): Configuracoes? = configuracoesDao.getConfiguracoesOnce()

    suspend fun salvarConfiguracoes(configuracoes: Configuracoes) {
        configuracoesDao.upsertConfiguracoes(configuracoes.copy(id = 1))
    }
}
