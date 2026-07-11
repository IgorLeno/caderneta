package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.NavigationActions
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.clickRecyclerChild
import com.example.caderneta.helpers.fillField
import com.example.caderneta.helpers.tapRecyclerCounterPlus
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fluxo integral configuração -> venda: define os preços pela UI de Ajustes e, em seguida,
 * registra uma venda que usa exatamente esses preços (dois salgados a prazo = R$ 12,00),
 * verificando Venda/Operação/Conta/saldo/ledger.
 */
class ConfigParaVendaE2ETest : BaseE2ETest() {
    @Test
    fun configuraPrecosPelaUiERegistraVendaComEssesPrecos() {
        val scenarioName = "config_para_venda"
        val seeded = DatabaseFixture.seedClienteBasico(app.container)

        launch(scenarioName).use {
            step(scenarioName, "config_precos")
            NavigationActions.openAjustes()
            fillField(R.id.et_salgado_vista, "5,00")
            fillField(R.id.et_salgado_prazo, "6,00")
            fillField(R.id.et_suco_vista, "3,00")
            fillField(R.id.et_suco_prazo, "4,00")
            onView(withId(R.id.btn_salvar_configuracoes)).perform(scrollTo(), click())
            onView(withText("Configurações salvas")).check(matches(isDisplayed()))
            WaitConditions.awaitDb {
                app.container.configuracoesRepository
                    .getConfiguracoesOnce()
                    ?.precoSalgadoPrazoCentavos == 600L
            }

            step(scenarioName, "registrar_venda")
            NavigationActions.openVendas()
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withText("Audit Local Norte")).perform(click())
            onView(withText("Cliente Auditoria")).check(matches(isDisplayed()))

            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnVenda)
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnAPrazo)
            tapRecyclerCounterPlus(R.id.rv_clientes, 0, R.id.contador_salgados, times = 2)
            screenshot(scenarioName, "venda_2_salgados_prazo")
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btn_confirmar_operacao)
            onView(withText("Venda registrada com sucesso!")).check(matches(isDisplayed()))

            WaitConditions.awaitDb {
                app.container.contaRepository.getSaldoCentavos(seeded.clienteId) == 1200L
            }
            val venda =
                runBlocking {
                    app.container.database
                        .backupDao()
                        .getAllVendas()
                }.single()
            assertEquals(TransacaoVenda.A_PRAZO, venda.transacao)
            assertEquals(2, venda.quantidadeSalgados)
            assertEquals(1200L, venda.valorCentavos)
            assertEquals(
                1200L,
                runBlocking {
                    app.container.contaRepository
                        .getContaByCliente(seeded.clienteId)
                        ?.saldoCentavos
                },
            )
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
}
