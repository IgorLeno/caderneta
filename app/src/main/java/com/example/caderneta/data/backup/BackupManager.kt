package com.example.caderneta.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Conta
import com.example.caderneta.data.entity.TransacaoVenda
import java.io.InputStream
import java.io.OutputStream

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val serializer: BackupSerializer = BackupSerializer(),
    private val validator: BackupValidator = BackupValidator(context.packageName),
) {
    private val backupDao = db.backupDao()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun exportar(uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { output -> exportar(output) }
            ?: error("Não foi possível abrir destino do backup")
    }

    suspend fun exportar(outputStream: OutputStream) {
        val snapshot = criarSnapshot()
        outputStream.writer(Charsets.UTF_8).use { writer -> writer.write(serializer.toJson(snapshot)) }
        prefs.edit().putLong(KEY_ULTIMO_BACKUP, snapshot.geradoEmMillis).apply()
    }

    suspend fun lerResumo(uri: Uri): Pair<BackupSnapshot, BackupResumo> =
        context.contentResolver.openInputStream(uri)?.use { input -> lerResumo(input) }
            ?: error("Não foi possível abrir backup")

    fun lerResumo(inputStream: InputStream): Pair<BackupSnapshot, BackupResumo> {
        val snapshot = serializer.fromJson(inputStream.reader(Charsets.UTF_8).readText())
        validator.validar(snapshot)
        return snapshot to
            BackupResumo(
                clientes = snapshot.clientes.size,
                lancamentos = snapshot.vendas.size,
                geradoEmMillis = snapshot.geradoEmMillis,
            )
    }

    suspend fun restaurar(snapshot: BackupSnapshot) {
        validator.validar(snapshot)
        db.withTransaction {
            backupDao.deleteVendas()
            backupDao.deleteOperacoes()
            backupDao.deleteContas()
            backupDao.deleteClientes()
            backupDao.deleteLocais()
            backupDao.deleteConfiguracoes()

            backupDao.insertConfiguracoes(snapshot.configuracoes)
            backupDao.insertLocais(snapshot.locais.sortedWith(compareBy({ it.level }, { it.id })))
            backupDao.insertClientes(snapshot.clientes)
            backupDao.insertOperacoes(snapshot.operacoes)
            backupDao.insertVendas(snapshot.vendas)
            backupDao.insertContas(recalcularContas(snapshot))
        }
    }

    fun getUltimoBackupMillis(): Long? = prefs.getLong(KEY_ULTIMO_BACKUP, 0L).takeIf { it > 0L }

    private suspend fun criarSnapshot(): BackupSnapshot =
        db.withTransaction {
            BackupSnapshot(
                app = context.packageName,
                geradoEmMillis = System.currentTimeMillis(),
                locais = backupDao.getAllLocais(),
                clientes = backupDao.getAllClientes(),
                operacoes = backupDao.getAllOperacoes(),
                vendas = backupDao.getAllVendas(),
                contas = backupDao.getAllContas(),
                configuracoes = backupDao.getAllConfiguracoes(),
            )
        }

    private fun recalcularContas(snapshot: BackupSnapshot): List<Conta> {
        val saldos = snapshot.clientes.associate { it.id to 0L }.toMutableMap()
        snapshot.vendas.forEach { venda ->
            val delta =
                when (venda.transacao) {
                    TransacaoVenda.A_PRAZO -> venda.valorCentavos
                    TransacaoVenda.PAGAMENTO -> -venda.valorCentavos
                    TransacaoVenda.A_VISTA -> 0L
                }
            saldos[venda.clienteId] = (saldos[venda.clienteId] ?: 0L) + delta
        }
        require(saldos.values.all { it >= 0L }) { "Backup geraria saldo negativo" }
        return saldos.map { (clienteId, saldo) -> Conta(clienteId = clienteId, saldoCentavos = saldo) }
    }

    private companion object {
        const val PREFS_NAME = "backup_prefs"
        const val KEY_ULTIMO_BACKUP = "ultimo_backup_millis"
    }
}
