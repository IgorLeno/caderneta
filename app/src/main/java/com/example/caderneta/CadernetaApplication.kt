package com.example.caderneta

import android.app.Application

open class CadernetaApplication : Application() {
    open val container: AppContainer by lazy { ProductionAppContainer(this) }

    val database get() = container.database
    val clienteRepository get() = container.clienteRepository
    val localRepository get() = container.localRepository
    val vendaRepository get() = container.vendaRepository
    val configuracoesRepository get() = container.configuracoesRepository
    val contaRepository get() = container.contaRepository
    val financeiroService get() = container.financeiroService
    val backupManager get() = container.backupManager
    val clientePhotoRepository get() = container.clientePhotoRepository
    val clientePhotoSource get() = container.clientePhotoSource
}
