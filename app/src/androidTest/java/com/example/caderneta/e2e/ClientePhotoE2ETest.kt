package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.clickByResourceName
import com.example.caderneta.helpers.clickTextInputEndIcon
import com.example.caderneta.helpers.fillVisibleField
import com.example.caderneta.helpers.longClickRecyclerItem
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class ClientePhotoE2ETest : BaseE2ETest() {
    @Test
    fun adicionaTrocaRemoveFotoEExportaBackupPelaUi() {
        val scenarioName = "cliente_photo_crud_backup"
        launch(scenarioName).use {
            try {
                step(scenarioName, "criar_local")
                criarLocalPelaUi()
                step(scenarioName, "criar_cliente_com_foto")
                criarClienteComFotoPelaUi()
                val primeiraFoto = awaitFotoNome()
                assertPhotoExists(primeiraFoto)

                step(scenarioName, "exportar_backup_com_foto")
                val backupBytes =
                    ByteArrayOutputStream()
                        .also { output -> runBlocking { app.container.backupManager.exportar(output) } }
                        .toByteArray()
                val backupPayload =
                    app.container.backupManager
                        .lerResumo(backupBytes.inputStream())
                        .first
                assertTrue(backupPayload.fotos.containsKey(primeiraFoto))
                assertTrue(backupPayload.fotos.getValue(primeiraFoto).isNotEmpty())

                step(scenarioName, "trocar_foto")
                trocarFotoPelaUi()
                val segundaFoto = awaitFotoNomeDiferenteDe(primeiraFoto)
                assertNotEquals(primeiraFoto, segundaFoto)
                assertPhotoMissing(primeiraFoto)
                assertPhotoExists(segundaFoto)

                step(scenarioName, "remover_foto")
                removerFotoPelaUi()
                WaitConditions.awaitDb { cliente().fotoNome == null }
                assertPhotoMissing(segundaFoto)
                assertEquals(null, cliente().fotoNome)
            } finally {
                runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
            }
        }
    }

    private fun criarLocalPelaUi() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        fillVisibleField(R.id.et_novo_local, "Audit Local Foto")
        clickTextInputEndIcon(R.id.til_novo_local)
        WaitConditions.awaitDb {
            app.container.database
                .backupDao()
                .getAllLocais()
                .any { local -> local.nome == "Audit Local Foto" }
        }
        onView(withText("Audit Local Foto")).perform(click())
    }

    private fun criarClienteComFotoPelaUi() {
        onView(withId(R.id.fab_novo_cliente)).perform(click())
        fillVisibleField(R.id.et_nome, CLIENTE_NOME)
        fillVisibleField(R.id.et_telefone, "11999990000")
        onView(withId(R.id.btn_importar)).perform(click())
        onView(withId(R.id.btn_adicionar_foto)).perform(click())
        onView(withText("Escolher imagem")).perform(click())
        WaitConditions.awaitView(withId(R.id.btn_remover_foto))
        onView(withId(R.id.btn_remover_foto)).check(matches(isDisplayed()))
        onView(withText("Adicionar")).perform(click())
    }

    private fun trocarFotoPelaUi() {
        longClickRecyclerItem(R.id.rv_clientes, 0)
        clickByResourceName("btn_editar")
        onView(withId(R.id.btn_adicionar_foto)).perform(click())
        onView(withText("Tirar foto")).perform(click())
        WaitConditions.awaitView(withId(R.id.btn_remover_foto))
        onView(withId(R.id.btn_importar)).perform(click())
        onView(withId(R.id.spinner_local)).check(matches(withText("Audit Local Foto")))
        onView(withText("Salvar")).perform(click())
    }

    private fun removerFotoPelaUi() {
        longClickRecyclerItem(R.id.rv_clientes, 0)
        clickByResourceName("btn_editar")
        onView(withId(R.id.btn_remover_foto)).perform(click())
        onView(withId(R.id.btn_importar)).perform(click())
        onView(withId(R.id.spinner_local)).check(matches(withText("Audit Local Foto")))
        onView(withText("Salvar")).perform(click())
    }

    private fun awaitFotoNome(): String {
        WaitConditions.awaitDb { cliente().fotoNome != null }
        return requireNotNull(cliente().fotoNome)
    }

    private fun awaitFotoNomeDiferenteDe(previous: String): String {
        WaitConditions.awaitDb {
            val atual = cliente().fotoNome
            atual != null && atual != previous
        }
        return requireNotNull(cliente().fotoNome)
    }

    private fun cliente() =
        runBlocking {
            requireNotNull(
                app.container.database
                    .backupDao()
                    .getAllClientes()
                    .singleOrNull { cliente -> cliente.nome == CLIENTE_NOME },
            )
        }

    private fun assertPhotoExists(fotoNome: String) {
        val file = app.container.clientePhotoRepository.arquivo(fotoNome)
        assertNotNull(file)
        assertTrue(file?.isFile == true)
        assertTrue(file?.length() ?: 0L > 0L)
    }

    private fun assertPhotoMissing(fotoNome: String) {
        assertFalse(
            app.container.clientePhotoRepository
                .arquivo(fotoNome)
                ?.exists() == true,
        )
    }

    private companion object {
        const val CLIENTE_NOME = "Cliente Auditoria Foto"
    }
}
