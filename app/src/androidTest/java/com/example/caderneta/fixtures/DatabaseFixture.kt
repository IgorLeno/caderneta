package com.example.caderneta.fixtures

import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.caderneta.AppContainer
import com.example.caderneta.data.backup.BackupSnapshot
import kotlinx.coroutines.runBlocking

object DatabaseFixture {
    fun reset(container: AppContainer) {
        runBlocking {
            container.backupManager.restaurar(emptySnapshot())
        }
    }

    fun seedConfiguracoes(container: AppContainer) {
        runBlocking {
            container.configuracoesRepository.salvarConfiguracoes(TestFixtures.configuracoesBasicas())
        }
    }

    fun seedClienteBasico(container: AppContainer): SeededCliente =
        runBlocking {
            val localId = container.localRepository.insertLocal(TestFixtures.localPrincipal(id = 0))
            val clienteId = container.clienteRepository.insertCliente(TestFixtures.cliente(localId = localId, id = 0))
            SeededCliente(localId = localId, clienteId = clienteId)
        }

    fun assertForeignKeysOk(container: AppContainer) {
        runBlocking {
            val violations =
                container.database.backupDao().foreignKeyCheck(
                    SimpleSQLiteQuery("PRAGMA foreign_key_check"),
                )
            check(violations.isEmpty()) { "Foreign key violations: $violations" }
        }
    }

    private fun emptySnapshot(): BackupSnapshot =
        BackupSnapshot(
            app = "com.example.caderneta",
            geradoEmMillis = System.currentTimeMillis(),
            locais = emptyList(),
            clientes = emptyList(),
            operacoes = emptyList(),
            vendas = emptyList(),
            contas = emptyList(),
            configuracoes = emptyList(),
        )
}

data class SeededCliente(
    val localId: Long,
    val clienteId: Long,
)
