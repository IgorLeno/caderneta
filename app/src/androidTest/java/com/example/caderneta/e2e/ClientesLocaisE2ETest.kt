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
import com.example.caderneta.helpers.clickByAnyText
import com.example.caderneta.helpers.clickByResourceName
import com.example.caderneta.helpers.clickTextInputEndIcon
import com.example.caderneta.helpers.fillVisibleField
import com.example.caderneta.helpers.longClickRecyclerItem
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientesLocaisE2ETest : BaseE2ETest() {
    @Test
    fun criaEditaEExcluiClienteComLocalPelaUi() {
        val scenarioName = "clientes_locais_crud"
        launch(scenarioName).use {
            criarLocalPelaUi()
            criarClientePelaUi("Cliente Auditoria UI", "11988887777")
            WaitConditions.awaitDb {
                app.container.database.backupDao().getAllClientes().any { cliente ->
                    cliente.nome == "Cliente Auditoria UI"
                }
            }
            onView(withText("Cliente Auditoria UI")).check(matches(isDisplayed()))
            screenshot(scenarioName, "cliente_criado")

            editarClientePelaUi("Cliente Auditoria Editado", "11911112222")
            WaitConditions.awaitDb {
                app.container.database.backupDao().getAllClientes().any { cliente ->
                    cliente.nome == "Cliente Auditoria Editado" && cliente.telefone == "11911112222"
                }
            }
            onView(withText("Cliente Auditoria Editado")).check(matches(isDisplayed()))
            screenshot(scenarioName, "cliente_editado")

            excluirClientePelaUi()
            WaitConditions.awaitDb {
                app.container.database
                    .backupDao()
                    .getAllClientes()
                    .isEmpty()
            }
            assertEquals(
                1,
                runBlocking {
                    app.container.database
                        .backupDao()
                        .getAllLocais()
                        .size
                },
            )
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
        }
    }

    private fun criarLocalPelaUi() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        fillVisibleField(R.id.et_novo_local, "Audit Local UI")
        clickTextInputEndIcon(R.id.til_novo_local)
        WaitConditions.awaitDb {
            app.container.database
                .backupDao()
                .getAllLocais()
                .any { local -> local.nome == "Audit Local UI" }
        }
        onView(withText("Audit Local UI")).perform(click())
    }

    private fun criarClientePelaUi(
        nome: String,
        telefone: String,
    ) {
        onView(withId(R.id.fab_novo_cliente)).perform(click())
        fillVisibleField(R.id.et_nome, nome)
        fillVisibleField(R.id.et_telefone, telefone)
        onView(withId(R.id.btn_importar)).perform(click())
        onView(withId(R.id.spinner_local)).check(matches(withText("Audit Local UI")))
        onView(withText("Adicionar")).perform(click())
    }

    private fun editarClientePelaUi(
        nome: String,
        telefone: String,
    ) {
        longClickRecyclerItem(R.id.rv_clientes, 0)
        clickByResourceName("btn_editar")
        fillVisibleField(R.id.et_nome, nome)
        fillVisibleField(R.id.et_telefone, telefone)
        onView(withId(R.id.btn_importar)).perform(click())
        onView(withId(R.id.spinner_local)).check(matches(withText("Audit Local UI")))
        onView(withText("Salvar")).perform(click())
    }

    private fun excluirClientePelaUi() {
        longClickRecyclerItem(R.id.rv_clientes, 0)
        clickByResourceName("btn_excluir")
        clickByAnyText("Excluir", "EXCLUIR")
    }
}
