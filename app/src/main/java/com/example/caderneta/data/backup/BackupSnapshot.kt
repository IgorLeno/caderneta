package com.example.caderneta.data.backup

import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Conta
import com.example.caderneta.data.entity.Local
import com.example.caderneta.data.entity.Operacao
import com.example.caderneta.data.entity.Venda

data class BackupSnapshot(
    val formatVersion: Int = 1,
    val dbVersion: Int = 1,
    val app: String,
    val geradoEmMillis: Long,
    val locais: List<Local>,
    val clientes: List<Cliente>,
    val operacoes: List<Operacao>,
    val vendas: List<Venda>,
    val contas: List<Conta>,
    val configuracoes: List<Configuracoes>,
)

data class BackupResumo(
    val clientes: Int,
    val lancamentos: Int,
    val geradoEmMillis: Long,
)
