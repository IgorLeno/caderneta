package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.data.entity.Local
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.clickByAnyText
import com.example.caderneta.helpers.clickRecyclerChild
import com.example.caderneta.helpers.clickTextInputEndIcon
import com.example.caderneta.helpers.fillByResourceName
import com.example.caderneta.helpers.fillVisibleField
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Cobre pela UI o menu de opções de um Local (adicionar sublocal / renomear / excluir),
 * incluindo o caminho de cancelar a exclusão — nenhum teste E2E cobria essas interações
 * antes desta fase (só o CRUD de Cliente era exercitado).
 */
class LocalMenuE2ETest : BaseE2ETest() {
    @Test
    fun adicionaSublocalRenomeiaEExcluiLocalPeloMenuDeOpcoes() {
        val scenarioName = "local_menu_crud"
        launch(scenarioName).use {
            try {
                step(scenarioName, "criar_local")
                criarLocalPelaUi("Audit Local Menu")

                step(scenarioName, "adicionar_sublocal")
                abrirMenuDoLocal(0)
                clickByAnyText("Adicionar sublocal", "ADICIONAR SUBLOCAL")
                fillByResourceName("et_nome_sublocal", "Sublocal Auditoria")
                clickByAnyText("Adicionar", "ADICIONAR")
                WaitConditions.awaitDb { localNomeado("Sublocal Auditoria") != null }
                screenshot(scenarioName, "sublocal_criado")

                step(scenarioName, "renomear_local")
                abrirMenuDoLocal(0)
                clickByAnyText("Renomear", "RENOMEAR")
                fillByResourceName("et_nome_local", "Audit Local Renomeado")
                clickByAnyText("Salvar", "SALVAR")
                WaitConditions.awaitDb { localNomeado("Audit Local Renomeado") != null }
                screenshot(scenarioName, "local_renomeado")

                step(scenarioName, "cancelar_exclusao")
                abrirMenuDoLocal(0)
                clickByAnyText("Excluir", "EXCLUIR")
                clickByAnyText("Não", "NÃO")
                assertNotNull(
                    "Local deveria continuar existindo apos cancelar a exclusao",
                    localNomeado("Audit Local Renomeado"),
                )

                step(scenarioName, "expandir_para_ver_sublocal")
                clickRecyclerChild(R.id.rv_locais, 0, R.id.btn_expandir)
                WaitConditions.awaitView(withText("Sublocal Auditoria"))

                step(scenarioName, "confirmar_exclusao_sublocal")
                // Exclui o sublocal (sem filhos/clientes) para exercitar exclusao real, nao arquivamento.
                abrirMenuDoLocal(1)
                clickByAnyText("Excluir", "EXCLUIR")
                clickByAnyText("Sim", "SIM")
                WaitConditions.awaitDb { localNomeado("Sublocal Auditoria") == null }
                assertNull(localNomeado("Sublocal Auditoria"))
                screenshot(scenarioName, "sublocal_excluido")
            } finally {
                runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
            }
        }
    }

    private fun criarLocalPelaUi(nome: String) {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        fillVisibleField(R.id.et_novo_local, nome)
        clickTextInputEndIcon(R.id.til_novo_local)
        WaitConditions.awaitDb { localNomeado(nome) != null }
    }

    private fun abrirMenuDoLocal(position: Int) {
        clickRecyclerChild(R.id.rv_locais, position, R.id.btn_menu_local)
    }

    private fun localNomeado(nome: String): Local? =
        runBlocking {
            app.container.database
                .backupDao()
                .getAllLocais()
                .singleOrNull { local -> local.nome == nome }
        }
}
