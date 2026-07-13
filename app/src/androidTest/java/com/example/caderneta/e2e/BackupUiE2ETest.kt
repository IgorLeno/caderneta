package com.example.caderneta.e2e

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.caderneta.R
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.data.entity.TipoTransacao
import com.example.caderneta.domain.foto.ClientePhotoStore
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.fixtures.TestFixtures
import com.example.caderneta.helpers.NavigationActions
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

class BackupUiE2ETest : BaseE2ETest() {
    private lateinit var backupFile: File

    @Before
    fun setUpIntents() {
        Intents.init()
        backupFile = File(app.cacheDir, "audit_backup/backup-ui-roundtrip.zip")
        backupFile.parentFile?.mkdirs()
        backupFile.delete()
    }

    @After
    fun releaseIntents() {
        Intents.release()
    }

    @Test
    fun exportaERestauraBackupComFotosPelosBotoesReais() {
        val scenarioName = "backup_ui_roundtrip"
        val seed = seedBackupFixture()
        val originalPhotoBytes =
            requireNotNull(app.container.clientePhotoRepository.arquivo(seed.fotoNome))
                .readBytes()
        stubCreateDocumentResult()

        launch(scenarioName).use {
            try {
                step(scenarioName, "exportar_pelo_botao")
                NavigationActions.openAjustes()
                onView(withId(R.id.btn_exportar_backup)).perform(scrollTo(), click())
                WaitConditions.awaitView(withText("Backup exportado"))
                assertTrue(backupFile.isFile)
                assertTrue(backupFile.length() > 0L)

                step(scenarioName, "limpar_estado")
                DatabaseFixture.reset(app.container)
                assertEquals(
                    emptyList<Cliente>(),
                    runBlocking {
                        app.container.database
                            .backupDao()
                            .getAllClientes()
                    },
                )
                assertEquals(null, app.container.clientePhotoRepository.arquivo(seed.fotoNome))

                step(scenarioName, "restaurar_pelo_botao")
                stubOpenDocumentResult()
                onView(withId(R.id.btn_restaurar_backup)).perform(scrollTo(), click())
                WaitConditions.awaitView(withText("Restaurar backup?"))
                onView(withText("Restaurar")).perform(click())

                WaitConditions.awaitDb {
                    app.container.database
                        .backupDao()
                        .getAllClientes()
                        .any { cliente -> cliente.id == seed.clienteId }
                }
                WaitConditions.awaitView(withText("Backup restaurado"))

                assertRestoredData(seed, originalPhotoBytes)
            } finally {
                runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
            }
        }
    }

    private fun seedBackupFixture(): SeededBackup {
        val fotoBytes = syntheticJpegBytes()
        return runBlocking {
            val config = TestFixtures.configuracoesBasicas()
            app.container.configuracoesRepository.salvarConfiguracoes(config)
            val localId = app.container.localRepository.insertLocal(TestFixtures.localPrincipal(id = 0))
            val clienteId =
                app.container.clienteRepository.insertCliente(
                    TestFixtures.cliente(localId = localId, id = 0).copy(nome = CLIENTE_NOME),
                )
            val fotoNome = "cliente_${clienteId}_backup-ui.jpg"
            ClientePhotoStore(app).writeAtomic(fotoNome, fotoBytes)
            val cliente = requireNotNull(app.container.clienteRepository.getClienteById(clienteId))
            app.container.clienteRepository.updateCliente(cliente.copy(fotoNome = fotoNome))
            app.container.financeiroService.registrarVenda(
                clienteId = clienteId,
                localId = localId,
                tipoTransacao = TipoTransacao.A_PRAZO,
                isPromocao = false,
                quantidadeSalgados = 2,
                quantidadeSucos = 1,
                valorCentavos = 1300L,
                promocaoDetalhes = null,
            )
            SeededBackup(localId = localId, clienteId = clienteId, fotoNome = fotoNome)
        }
    }

    private fun assertRestoredData(
        seed: SeededBackup,
        originalPhotoBytes: ByteArray,
    ) {
        val backupDao = app.container.database.backupDao()
        val cliente = runBlocking { backupDao.getAllClientes().single { it.id == seed.clienteId } }
        val vendas = runBlocking { backupDao.getAllVendas() }
        val operacoes = runBlocking { backupDao.getAllOperacoes() }
        val configuracoes = runBlocking { app.container.configuracoesRepository.getConfiguracoesOnce() }
        val saldoHistorico =
            runBlocking { app.container.financeiroService.calcularSaldoDoHistorico(seed.clienteId) }
        val conta =
            runBlocking {
                app.container.database
                    .contaDao()
                    .getContaByCliente(seed.clienteId)
            }
        val restoredPhotoFile = app.container.clientePhotoRepository.arquivo(seed.fotoNome)

        assertEquals(CLIENTE_NOME, cliente.nome)
        assertEquals(seed.localId, cliente.localId)
        assertEquals(seed.fotoNome, cliente.fotoNome)
        assertEquals(1, vendas.size)
        assertEquals(1, operacoes.size)
        assertEquals(1300L, vendas.single().valorCentavos)
        assertEquals(1300L, saldoHistorico)
        assertEquals(1300L, conta?.saldoCentavos)
        assertEquals(TestFixtures.configuracoesBasicas(), configuracoes)
        assertNotNull(restoredPhotoFile)
        assertArrayEquals(originalPhotoBytes, restoredPhotoFile?.readBytes())
        DatabaseFixture.assertForeignKeysOk(app.container)
    }

    private fun stubCreateDocumentResult() {
        Intents.intending(hasAction(Intent.ACTION_CREATE_DOCUMENT)).respondWith(activityResultForBackupFile())
    }

    private fun stubOpenDocumentResult() {
        Intents.intending(hasAction(Intent.ACTION_OPEN_DOCUMENT)).respondWith(activityResultForBackupFile())
    }

    private fun activityResultForBackupFile(): Instrumentation.ActivityResult =
        Instrumentation.ActivityResult(
            Activity.RESULT_OK,
            Intent()
                .setData(backupUri())
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        )

    private fun backupUri() =
        FileProvider.getUriForFile(
            InstrumentationRegistry.getInstrumentation().targetContext,
            "${app.packageName}.fileprovider",
            backupFile,
        )

    private fun syntheticJpegBytes(): ByteArray {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb(20, 140, 220))
            ByteArrayOutputStream().use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private data class SeededBackup(
        val localId: Long,
        val clienteId: Long,
        val fotoNome: String,
    )

    private companion object {
        const val CLIENTE_NOME = "Cliente Backup UI"
    }
}
