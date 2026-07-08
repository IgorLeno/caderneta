package com.example.caderneta

import android.app.Application
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.domain.FinanceiroService
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.OperacaoRepository
import com.example.caderneta.repository.VendaRepository

class CadernetaApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val clienteRepository by lazy { ClienteRepository(database.clienteDao()) }
    val localRepository by lazy { LocalRepository(database.localDao()) }
    val vendaRepository by lazy { VendaRepository(database.vendaDao()) }
    val configuracoesRepository by lazy { ConfiguracoesRepository(database.configuracoesDao()) }
    val operacaoRepository by lazy { OperacaoRepository(database.operacaoDao()) }
    val contaRepository by lazy { ContaRepository(database.contaDao()) }
    val financeiroService by lazy { FinanceiroService(database) }
}
