package com.example.caderneta

import android.content.Context
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.backup.BackupManager
import com.example.caderneta.domain.FinanceiroService
import com.example.caderneta.domain.foto.AuditClientePhotoSource
import com.example.caderneta.domain.foto.ClientePhotoProcessor
import com.example.caderneta.domain.foto.ClientePhotoRepository
import com.example.caderneta.domain.foto.ClientePhotoSource
import com.example.caderneta.domain.foto.ClientePhotoStore
import com.example.caderneta.domain.foto.ProductionClientePhotoSource
import com.example.caderneta.repository.ClienteRepository
import com.example.caderneta.repository.ConfiguracoesRepository
import com.example.caderneta.repository.ContaRepository
import com.example.caderneta.repository.LocalRepository
import com.example.caderneta.repository.VendaRepository

interface AppContainer {
    val database: AppDatabase
    val clienteRepository: ClienteRepository
    val localRepository: LocalRepository
    val vendaRepository: VendaRepository
    val configuracoesRepository: ConfiguracoesRepository
    val contaRepository: ContaRepository
    val financeiroService: FinanceiroService
    val backupManager: BackupManager
    val clientePhotoRepository: ClientePhotoRepository
    val clientePhotoSource: ClientePhotoSource
}

class ProductionAppContainer(
    context: Context,
) : AppContainer {
    override val database: AppDatabase by lazy { AppDatabase.getDatabase(context) }
    override val clienteRepository: ClienteRepository by lazy { ClienteRepository(database.clienteDao()) }
    override val localRepository: LocalRepository by lazy {
        LocalRepository(database.localDao(), database.clienteDao(), database)
    }
    override val vendaRepository: VendaRepository by lazy { VendaRepository(database.vendaDao()) }
    override val configuracoesRepository: ConfiguracoesRepository by lazy {
        ConfiguracoesRepository(database.configuracoesDao())
    }
    override val contaRepository: ContaRepository by lazy { ContaRepository(database.contaDao()) }
    override val financeiroService: FinanceiroService by lazy { FinanceiroService(database) }
    override val backupManager: BackupManager by lazy { BackupManager(context, database) }
    override val clientePhotoRepository: ClientePhotoRepository by lazy {
        ClientePhotoRepository(
            clienteRepository = clienteRepository,
            store = ClientePhotoStore(context),
            processor = ClientePhotoProcessor(context),
        )
    }
    override val clientePhotoSource: ClientePhotoSource by lazy {
        if (BuildConfig.IS_AUDIT) AuditClientePhotoSource() else ProductionClientePhotoSource
    }
}
