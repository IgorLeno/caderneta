package com.example.caderneta.repository

import com.example.caderneta.data.dao.ConfiguracaoDao
import com.example.caderneta.data.entity.Configuracoes
import kotlinx.coroutines.flow.Flow

class ConfiguracaoRepository(private val configuracaoDao: ConfiguracaoDao) {
    fun getConfiguracoes(): Flow<Configuracoes?> = configuracaoDao.getConfiguracoes()
    suspend fun updateConfiguracoes(configuracoes: Configuracoes) = configuracaoDao.updateConfiguracoes(configuracoes)
}