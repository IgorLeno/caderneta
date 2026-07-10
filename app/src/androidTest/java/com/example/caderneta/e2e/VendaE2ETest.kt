package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.clickRecyclerChild
import com.example.caderneta.helpers.tapRecyclerCounterPlus
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class VendaE2ETest : BaseE2ETest() {
    @Test
    fun registraVendaAPrazoPelaUiEAtualizaLedgerESaldo() {
        val scenarioName = "venda_a_prazo"
        DatabaseFixture.seedConfiguracoes(app.container)
        val seeded = DatabaseFixture.seedClienteBasico(app.container)

        launch(scenarioName).use {
            selecionarLocal()
            onView(withText("Cliente Auditoria")).check(matches(isDisplayed()))
            screenshot(scenarioName, "cliente_pronto")

            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnVenda)
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnAPrazo)
            tapRecyclerCounterPlus(R.id.rv_clientes, 0, R.id.contador_salgados, times = 2)
            screenshot(scenarioName, "venda_2_salgados_prazo")
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btn_confirmar_operacao)
            onView(withText("Venda registrada com sucesso!")).check(matches(isDisplayed()))

            WaitConditions.awaitDb {
                app.container.contaRepository.getSaldoCentavos(seeded.clienteId) == 1200L
            }
            val vendas =
                runBlocking {
                    app.container.database
                        .backupDao()
                        .getAllVendas()
                }
            val operacoes =
                runBlocking {
                    app.container.database
                        .backupDao()
                        .getAllOperacoes()
                }
            val conta = runBlocking { app.container.contaRepository.getContaByCliente(seeded.clienteId) }
            assertEquals(1, vendas.size)
            assertEquals(1, operacoes.size)
            assertEquals(TransacaoVenda.A_PRAZO, vendas.single().transacao)
            assertEquals(2, vendas.single().quantidadeSalgados)
            assertEquals(0, vendas.single().quantidadeSucos)
            assertEquals(1200L, vendas.single().valorCentavos)
            assertEquals(1200L, conta?.saldoCentavos)
            assertEquals(
                1200L,
                runBlocking {
                    app.container.database
                        .vendaDao()
                        .calcularSaldoHistorico(seeded.clienteId)
                },
            )
            DatabaseFixture.assertForeignKeysOk(app.container)
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container, seeded.clienteId) }
        }
    }

    private fun selecionarLocal() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withText("Audit Local Norte")).perform(click())
    }
}
