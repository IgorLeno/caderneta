package com.example.caderneta.e2e

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.caderneta.R
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.data.backup.BackupPayload
import com.example.caderneta.data.backup.BackupSnapshot
import com.example.caderneta.data.entity.Cliente
import com.example.caderneta.fixtures.TestFixtures
import com.example.caderneta.helpers.NavigationActions
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.scrollRecyclerToPosition
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Locale

class PhotoListPerformanceE2ETest : BaseE2ETest() {
    @Test
    fun listaComCemClientesComFotoRolaEmVendasEConsultas() {
        val scenarioName = "photo_list_100"
        seedClientesComFotos(CLIENT_COUNT)
        UiDevice
            .getInstance(InstrumentationRegistry.getInstrumentation())
            .collapseSystemPanels()

        launch(scenarioName).use {
            WaitConditions.awaitView(withText(clienteNome(1)))
            screenshot(scenarioName, "vendas_topo")

            scrollRecyclerToPosition(R.id.rv_clientes, CLIENT_COUNT - 1)
            WaitConditions.awaitView(withText(clienteNome(CLIENT_COUNT)))
            screenshot(scenarioName, "vendas_fim")

            NavigationActions.openConsultas()
            WaitConditions.awaitView(withText(clienteNome(1)))
            scrollRecyclerToPosition(R.id.rv_resultados, CLIENT_COUNT - 1)
            WaitConditions.awaitView(withText(clienteNome(CLIENT_COUNT)))
            onView(withText(clienteNome(CLIENT_COUNT))).check(matches(isDisplayed()))
            screenshot(scenarioName, "consultas_fim")

            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
        }
    }

    private fun seedClientesComFotos(count: Int) {
        val local = TestFixtures.localPrincipal(id = LOCAL_ID)
        val clientes =
            (1..count).map { index ->
                Cliente(
                    id = index.toLong(),
                    nome = clienteNome(index),
                    telefone = "11999${index.toString().padStart(5, '0')}",
                    localId = LOCAL_ID,
                    fotoNome = fotoNome(index),
                )
            }
        val fotos = clientes.associate { cliente -> requireNotNull(cliente.fotoNome) to jpegBytes(cliente.id.toInt()) }

        val snapshot =
            BackupSnapshot(
                formatVersion = 2,
                dbVersion = AppDatabase.DATABASE_VERSION,
                app = app.packageName,
                geradoEmMillis = System.currentTimeMillis(),
                locais = listOf(local),
                clientes = clientes,
                operacoes = emptyList(),
                vendas = emptyList(),
                contas = emptyList(),
                configuracoes = listOf(TestFixtures.configuracoesBasicas()),
            )

        runBlocking {
            app.container.backupManager.restaurar(BackupPayload(snapshot, fotos))
            assertEquals(
                count,
                app.container.database
                    .backupDao()
                    .getAllClientes()
                    .size,
            )
        }
    }

    private fun jpegBytes(index: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(PHOTO_SIZE_PX, PHOTO_SIZE_PX, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.rgb((index * 31) % 255, (index * 47) % 255, (index * 59) % 255))
            ByteArrayOutputStream().use { output ->
                require(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                    "Falha ao gerar fixture JPEG"
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun clienteNome(index: Int): String = String.format(Locale.US, "Cliente Foto %03d", index)

    private fun fotoNome(index: Int): String = "cliente_${index}_perf.jpg"

    private fun UiDevice.collapseSystemPanels() {
        executeShellCommand("cmd statusbar collapse")
        waitForIdle()
    }

    private companion object {
        const val LOCAL_ID = 1L
        const val CLIENT_COUNT = 100
        const val PHOTO_SIZE_PX = 32
        const val JPEG_QUALITY = 85
    }
}
