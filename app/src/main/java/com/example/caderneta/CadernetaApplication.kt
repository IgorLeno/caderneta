package com.example.caderneta

import android.app.Application
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.repository.*

class CadernetaApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val clienteRepository by lazy { ClienteRepository(database.clienteDao()) }
    val localRepository by lazy { LocalRepository(database.localDao()) }
    val produtoRepository by lazy { ProdutoRepository(database.produtoDao()) }
    val vendaRepository by lazy { VendaRepository(database.vendaDao()) }
    val itemVendaRepository by lazy { ItemVendaRepository(database.itemVendaDao()) }
    val configuracoesRepository by lazy { ConfiguracoesRepository(database.configuracoesDao()) }
    val operacaoRepository by lazy { OperacaoRepository(database.operacaoDao()) }
    val contaRepository by lazy { ContaRepository(database.contaDao()) }
}