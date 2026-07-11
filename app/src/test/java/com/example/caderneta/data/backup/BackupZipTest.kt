package com.example.caderneta.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.Configuracoes
import com.example.caderneta.data.entity.Local
import com.example.caderneta.domain.foto.ClientePhotoStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupZipTest {
    private lateinit var executor: ExecutorService
    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager
    private lateinit var store: ClientePhotoStore
    private lateinit var context: Context

    @Before
    fun setUp() {
        executor = Executors.newSingleThreadExecutor()
        context = ApplicationProvider.getApplicationContext()
        File(context.filesDir, ClientePhotoStore.PHOTO_DIR).deleteRecursively()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .setTransactionExecutor(executor)
                .build()
        store = ClientePhotoStore(context)
        manager = BackupManager(context, db, store = store)
    }

    @After
    fun tearDown() {
        db.close()
        executor.shutdownNow()
        File(context.filesDir, ClientePhotoStore.PHOTO_DIR).deleteRecursively()
    }

    @Test
    fun exportaERestauraZipComFoto() =
        runTest {
            val localId = db.localDao().insertLocal(Local(nome = "Local"))
            val clienteId = db.clienteDao().insertCliente(Cliente(nome = "Cliente", telefone = null, localId = localId))
            db.configuracoesDao().insertConfiguracoes(config())
            val fotoNome = store.photoNameFor(clienteId)
            val fotoBytes = jpegBytes(byteArrayOf(1, 2, 3, 4, 5))
            store.writeAtomic(fotoNome, fotoBytes)
            db.clienteDao().updateCliente(
                db.clienteDao().getClienteById(clienteId)!!.copy(fotoNome = fotoNome),
            )

            val output = ByteArrayOutputStream()
            manager.exportar(output)
            val zip = output.toByteArray()

            // Simula outro dispositivo: apaga a foto do disco antes de restaurar.
            store.delete(fotoNome)
            assertNull(store.existingPhotoFile(fotoNome))

            val (payload) = manager.lerResumo(ByteArrayInputStream(zip))
            assertEquals(fotoBytes.size, payload.fotos[fotoNome]?.size)
            manager.restaurar(payload)

            val restaurada = store.existingPhotoFile(fotoNome)
            assertNotNull(restaurada)
            assertArrayEquals(fotoBytes, restaurada!!.readBytes())
            assertEquals(fotoNome, db.clienteDao().getClienteById(clienteId)?.fotoNome)
        }

    @Test
    fun restaurarRemoveFotosOrfas() =
        runTest {
            val localId = db.localDao().insertLocal(Local(nome = "Local"))
            db.clienteDao().insertCliente(Cliente(nome = "Cliente", telefone = null, localId = localId))
            db.configuracoesDao().insertConfiguracoes(config())
            store.writeAtomic(store.photoNameFor(99), jpegBytes(byteArrayOf(9)))

            val output = ByteArrayOutputStream()
            manager.exportar(output)
            val (payload) = manager.lerResumo(ByteArrayInputStream(output.toByteArray()))
            manager.restaurar(payload)

            assertNull(store.existingPhotoFile(store.photoNameFor(99)))
        }

    @Test
    fun restauraJsonV1LegadoSemFotos() =
        runTest {
            db.localDao().insertLocal(Local(nome = "Ruido"))
            val snapshotV1 =
                BackupSnapshot(
                    formatVersion = 1,
                    dbVersion = 2,
                    app = context.packageName,
                    geradoEmMillis = 1,
                    locais = listOf(Local(id = 1, nome = "Escola")),
                    clientes = listOf(Cliente(id = 1, nome = "Cliente", telefone = null, localId = 1)),
                    operacoes = emptyList(),
                    vendas = emptyList(),
                    contas = emptyList(),
                    configuracoes = listOf(config()),
                )
            val json = BackupSerializer().toJson(snapshotV1)

            val (payload, resumo) = manager.lerResumo(ByteArrayInputStream(json.encodeToByteArray()))
            assertTrue(payload.fotos.isEmpty())
            assertEquals(1, resumo.clientes)
            manager.restaurar(payload)

            assertEquals(
                1,
                db
                    .clienteDao()
                    .getAllClientes()
                    .first()
                    .size,
            )
        }

    @Test
    fun rejeitaZipComPathTraversal() {
        val malicioso =
            zipCom(
                "../evil.jpg" to jpegBytes(byteArrayOf(1)),
            )
        assertRejeitado(malicioso)
    }

    @Test
    fun rejeitaZipComChecksumDivergente() {
        val fotoNome = "cliente_1.jpg"
        val bytes = jpegBytes(byteArrayOf(1, 2, 3))
        val manifest =
            """
            {"formatVersion":2,"app":"${context.packageName}","dbVersion":2,"geradoEmMillis":1,
            "fotos":[{"nome":"$fotoNome","sha256":"deadbeef","tamanho":${bytes.size}}]}
            """.trimIndent()
        val malicioso =
            zipCom(
                BackupArchive.ENTRY_MANIFEST to manifest.encodeToByteArray(),
                BackupArchive.ENTRY_DATA to "{}".encodeToByteArray(),
                "${BackupArchive.PHOTO_PREFIX}$fotoNome" to bytes,
            )
        assertRejeitado(malicioso)
    }

    @Test
    fun rejeitaZipMalformado() {
        // Cabeçalho PK\x03\x04 seguido de lixo — detectado como ZIP mas ilegível.
        val malformado = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x11, 0x22, 0x33)
        assertRejeitado(malformado)
    }

    private fun assertRejeitado(bytes: ByteArray) {
        var falhou = false
        try {
            manager.lerResumo(ByteArrayInputStream(bytes))
        } catch (_: Exception) {
            falhou = true
        }
        assertTrue(falhou)
    }

    private fun zipCom(vararg entradas: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entradas.forEach { (nome, conteudo) ->
                zip.putNextEntry(ZipEntry(nome))
                zip.write(conteudo)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun jpegBytes(payload: ByteArray): ByteArray =
        byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + payload + byteArrayOf(0xFF.toByte(), 0xD9.toByte())

    private fun config() =
        Configuracoes(
            precoSalgadoVistaCentavos = 500,
            precoSalgadoPrazoCentavos = 600,
            precoSucoVistaCentavos = 300,
            precoSucoPrazoCentavos = 400,
            promocoesAtivadas = false,
            promo1Nome = "Promo 1",
            promo1Salgados = 1,
            promo1Sucos = 1,
            promo1VistaCentavos = 700,
            promo1PrazoCentavos = 800,
            promo2Nome = "Promo 2",
            promo2Salgados = 2,
            promo2Sucos = 2,
            promo2VistaCentavos = 900,
            promo2PrazoCentavos = 1000,
        )
}
