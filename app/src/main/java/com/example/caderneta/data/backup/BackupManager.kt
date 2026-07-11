package com.example.caderneta.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Conta
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.domain.foto.ClientePhotoStore
import java.io.InputStream
import java.io.OutputStream

class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val serializer: BackupSerializer = BackupSerializer(),
    private val validator: BackupValidator = BackupValidator(context.packageName),
    private val store: ClientePhotoStore = ClientePhotoStore(context),
    private val archive: BackupArchive = BackupArchive(),
) {
    private val backupDao = db.backupDao()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun exportar(uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { output -> exportar(output) }
            ?: error("Não foi possível abrir destino do backup")
    }

    suspend fun exportar(outputStream: OutputStream) {
        val snapshot = criarSnapshot()
        val fotos = coletarFotos(snapshot.clientes)
        val manifest =
            BackupManifest(
                formatVersion = BackupArchive.FORMAT_VERSION_ZIP,
                app = snapshot.app,
                dbVersion = snapshot.dbVersion,
                geradoEmMillis = snapshot.geradoEmMillis,
                fotos =
                    fotos.map { (nome, bytes) ->
                        FotoManifest(nome, BackupArchive.sha256Hex(bytes), bytes.size.toLong())
                    },
            )
        archive.write(outputStream, serializer.toJson(snapshot), manifest, fotos)
        prefs.edit().putLong(KEY_ULTIMO_BACKUP, snapshot.geradoEmMillis).apply()
    }

    suspend fun lerResumo(uri: Uri): Pair<BackupPayload, BackupResumo> =
        context.contentResolver.openInputStream(uri)?.use { input -> lerResumo(input) }
            ?: error("Não foi possível abrir backup")

    fun lerResumo(inputStream: InputStream): Pair<BackupPayload, BackupResumo> {
        val bytes = inputStream.readBytes()
        val payload = if (isZip(bytes)) lerZip(bytes) else lerJson(bytes)
        validator.validar(payload.snapshot)
        return payload to
            BackupResumo(
                clientes = payload.snapshot.clientes.size,
                lancamentos = payload.snapshot.vendas.size,
                geradoEmMillis = payload.snapshot.geradoEmMillis,
            )
    }

    suspend fun restaurar(snapshot: BackupSnapshot) = restaurar(BackupPayload(snapshot))

    suspend fun restaurar(payload: BackupPayload) {
        validator.validar(payload.snapshot)
        db.withTransaction {
            backupDao.deleteVendas()
            backupDao.deleteOperacoes()
            backupDao.deleteContas()
            backupDao.deleteClientes()
            backupDao.deleteLocais()
            backupDao.deleteConfiguracoes()

            backupDao.insertConfiguracoes(payload.snapshot.configuracoes)
            backupDao.insertLocais(payload.snapshot.locais.sortedWith(compareBy({ it.level }, { it.id })))
            backupDao.insertClientes(payload.snapshot.clientes)
            backupDao.insertOperacoes(payload.snapshot.operacoes)
            backupDao.insertVendas(payload.snapshot.vendas)
            backupDao.insertContas(recalcularContas(payload.snapshot))
            val violations = backupDao.foreignKeyCheck(SimpleSQLiteQuery("PRAGMA foreign_key_check"))
            require(violations.isEmpty()) { "Backup restaurado violaria chaves estrangeiras" }
        }
        restaurarFotos(payload)
    }

    fun getUltimoBackupMillis(): Long? = prefs.getLong(KEY_ULTIMO_BACKUP, 0L).takeIf { it > 0L }

    private fun lerZip(bytes: ByteArray): BackupPayload {
        val content = archive.read(bytes)
        val snapshot = serializer.fromJson(content.dataJson)
        require(snapshot.formatVersion == BackupArchive.FORMAT_VERSION_ZIP) { "Versão de backup não suportada" }
        require(content.manifest.app == snapshot.app) { "Manifesto e dados de aplicativos diferentes" }
        return BackupPayload(snapshot, content.fotos)
    }

    private fun lerJson(bytes: ByteArray): BackupPayload = BackupPayload(serializer.fromJson(bytes.decodeToString()))

    /**
     * Grava as fotos referenciadas presentes no backup e remove as órfãs, mantendo o diretório
     * de fotos coerente com o banco restaurado. Fotos referenciadas mas ausentes no backup são
     * toleradas (o cliente fica sem imagem em disco, exibindo o avatar padrão).
     */
    private fun restaurarFotos(payload: BackupPayload) {
        val referenciadas =
            payload.snapshot.clientes
                .mapNotNull { it.fotoNome }
                .toSet()
        referenciadas.forEach { nome ->
            payload.fotos[nome]?.let { bytes -> store.writeAtomic(nome, bytes) }
        }
        store.deleteUnreferenced(referenciadas)
    }

    private fun coletarFotos(clientes: List<Cliente>): Map<String, ByteArray> {
        val fotos = LinkedHashMap<String, ByteArray>()
        clientes
            .mapNotNull { it.fotoNome }
            .distinct()
            .forEach { nome ->
                store.existingPhotoFile(nome)?.let { file -> fotos[nome] = file.readBytes() }
            }
        return fotos
    }

    private suspend fun criarSnapshot(): BackupSnapshot =
        db.withTransaction {
            BackupSnapshot(
                formatVersion = BackupArchive.FORMAT_VERSION_ZIP,
                dbVersion = AppDatabase.DATABASE_VERSION,
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

        fun isZip(bytes: ByteArray): Boolean =
            bytes.size >= 4 &&
                bytes[0] == 0x50.toByte() &&
                bytes[1] == 0x4B.toByte() &&
                bytes[2] == 0x03.toByte() &&
                bytes[3] == 0x04.toByte()
    }
}
