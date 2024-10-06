package com.example.caderneta.repository

import com.example.caderneta.data.dao.ConfiguracoesDao
import com.example.caderneta.data.entity.Configuracoes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConfiguracoesRepository(private val configuracoesDao: ConfiguracoesDao) {

    fun getConfiguracoes(): Flow<Configuracoes> {
        return configuracoesDao.getConfiguracoes().map { configuracoes ->
            configuracoes ?: Configuracoes(
                precoSalgadoVista = 0.0,
                precoSalgadoPrazo = 0.0,
                precoSucoVista = 0.0,
                precoSucoPrazo = 0.0,
                promocoesAtivadas = false,
                promo1Nome = "",
                promo1Salgados = 0,
                promo1Sucos = 0,
                promo1Vista = 0.0,
                promo1Prazo = 0.0,
                promo2Nome = "",
                promo2Salgados = 0,
                promo2Sucos = 0,
                promo2Vista = 0.0,
                promo2Prazo = 0.0
            )
        }
    }

    suspend fun salvarConfiguracoes(configuracoes: Configuracoes) {
        configuracoesDao.resetAndInsertConfiguracoes(configuracoes)
    }
}