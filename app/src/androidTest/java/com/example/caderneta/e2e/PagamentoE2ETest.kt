package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.data.entity.TransacaoVenda
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.clickRecyclerChild
import com.example.caderneta.helpers.replaceRecyclerChildText
import com.example.caderneta.helpers.tapRecyclerCounterPlus
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PagamentoE2ETest : BaseE2ETest() {
    @Test
    fun registraPagamentoParcialERejeitaPagamentoMaiorQueSaldoSemEscritaParcial() {
        val scenarioName = "pagamento_parcial_overdebt"
        DatabaseFixture.seedConfiguracoes(app.container)
        val seeded = DatabaseFixture.seedClienteBasico(app.container)

        launch(scenarioName).use {
            selecionarLocal()
            registrarVendaDeDozeReais()
            WaitConditions.awaitDb { app.container.contaRepository.getSaldoCentavos(seeded.clienteId) == 1200L }

            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnPagamento)
            replaceRecyclerChildText(R.id.rv_clientes, 0, R.id.et_valor_pagamento, "5,00")
            screenshot(scenarioName, "pagamento_500")
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btn_confirmar_pagamento)
            WaitConditions.awaitDb { app.container.contaRepository.getSaldoCentavos(seeded.clienteId) == 700L }
            screenshot(scenarioName, "pagamento_confirmado_saldo_700")

            val vendasAntesErro =
                runBlocking {
                    app.container.database
                        .backupDao()
                        .getAllVendas()
                        .size
                }
            val operacoesAntesErro =
                runBlocking {
                    app.container.database
                        .backupDao()
                        .getAllOperacoes()
                        .size
                }
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnPagamento)
            replaceRecyclerChildText(R.id.rv_clientes, 0, R.id.et_valor_pagamento, "8,00")
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btn_confirmar_pagamento)
            screenshot(scenarioName, "overdebt_rejected")

            val vendas =
                runBlocking {
                    app.container.database
                        .backupDao()
                        .getAllVendas()
                }
            val conta = runBlocking { app.container.contaRepository.getContaByCliente(seeded.clienteId) }
            assertEquals(vendasAntesErro, vendas.size)
            assertEquals(
                operacoesAntesErro,
                runBlocking {
                    app.container.database
                        .backupDao()
                        .getAllOperacoes()
                        .size
                },
            )
            assertEquals(700L, conta?.saldoCentavos)
            assertEquals(
                700L,
                runBlocking {
                    app.container.database
                        .vendaDao()
                        .calcularSaldoHistorico(seeded.clienteId)
                },
            )
            assertEquals(1, vendas.count { it.transacao == TransacaoVenda.PAGAMENTO })
            DatabaseFixture.assertForeignKeysOk(app.container)
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container, seeded.clienteId) }
        }
    }

    private fun selecionarLocal() {
        onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
        onView(withText("Audit Local Norte")).perform(click())
    }

    private fun registrarVendaDeDozeReais() {
        clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnVenda)
        clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnAPrazo)
        tapRecyclerCounterPlus(R.id.rv_clientes, 0, R.id.contador_salgados, times = 2)
        clickRecyclerChild(R.id.rv_clientes, 0, R.id.btn_confirmar_operacao)
    }
}
