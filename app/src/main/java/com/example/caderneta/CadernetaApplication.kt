package com.example.caderneta

import android.app.Application
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.backup.BackupManager
import com.example.caderneta.domain.FinanceiroService
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository

class CadernetaApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val clienteRepository by lazy { ClienteRepository(database.clienteDao()) }
    val localRepository by lazy { LocalRepository(database.localDao(), database) }
    val vendaRepository by lazy { VendaRepository(database.vendaDao()) }
    val configuracoesRepository by lazy { ConfiguracoesRepository(database.configuracoesDao()) }
    val contaRepository by lazy { ContaRepository(database.contaDao()) }
    val financeiroService by lazy { FinanceiroService(database) }
    val backupManager by lazy { BackupManager(this, database) }
}
