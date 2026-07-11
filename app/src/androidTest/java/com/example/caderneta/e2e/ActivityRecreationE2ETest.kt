package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.clickRecyclerChild
import com.example.caderneta.helpers.tapRecyclerCounterPlus
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Registra uma venda pela UI, recria a Activity e verifica
 * que configuração, cliente e saldo permanecem íntegros após a recriação.
 */
class ActivityRecreationE2ETest : BaseE2ETest() {
    @Test
    fun vendaSobreviveARecriacaoDaActivity() {
        val scenarioName = "activity_recreation"
        DatabaseFixture.seedConfiguracoes(app.container)
        val seeded = DatabaseFixture.seedClienteBasico(app.container)

        launch(scenarioName).use { scenario ->
            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            onView(withText("Audit Local Norte")).perform(click())
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnVenda)
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnAPrazo)
            tapRecyclerCounterPlus(R.id.rv_clientes, 0, R.id.contador_salgados, times = 2)
            clickRecyclerChild(R.id.rv_clientes, 0, R.id.btn_confirmar_operacao)
            WaitConditions.awaitDb {
                app.container.contaRepository.getSaldoCentavos(seeded.clienteId) == 1200L
            }

            step(scenarioName, "recreate")
            scenario.recreate()

            onView(withText("Cliente Auditoria")).check(matches(isDisplayed()))
            assertEquals(
                1200L,
                runBlocking { app.container.contaRepository.getSaldoCentavos(seeded.clienteId) },
            )
            assertEquals(
                500L,
                runBlocking {
                    app.container.configuracoesRepository
                        .getConfiguracoesOnce()
                        ?.precoSalgadoVistaCentavos
                },
            )
            DatabaseFixture.assertForeignKeysOk(app.container)
            screenshot(scenarioName, "apos_recriacao")
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container, seeded.clienteId) }
        }
    }
}
