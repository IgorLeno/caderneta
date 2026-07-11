package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.assertSnackbar
import com.example.caderneta.helpers.clickRecyclerChild
import com.example.caderneta.helpers.tapRecyclerCounterPlus
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Test

class VendaFeedbackE2ETest : BaseE2ETest() {
    @Test
    fun vendaRegistradaMostraSnackbarDeSucesso() {
        val scenarioName = "venda_feedback_snackbar"
        DatabaseFixture.seedConfiguracoes(app.container)
        val seeded = DatabaseFixture.seedClienteBasico(app.container)

        launch(scenarioName).use {
            try {
                onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
                onView(withText("Audit Local Norte")).perform(click())

                clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnVenda)
                clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnAPrazo)
                tapRecyclerCounterPlus(R.id.rv_clientes, 0, R.id.contador_salgados, times = 1)
                clickRecyclerChild(R.id.rv_clientes, 0, R.id.btn_confirmar_operacao)

                assertSnackbar("Venda registrada com sucesso!")
                WaitConditions.awaitDb {
                    app.container.contaRepository.getSaldoCentavos(seeded.clienteId) == 600L
                }
                DatabaseFixture.assertForeignKeysOk(app.container)
            } finally {
                runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container, seeded.clienteId) }
            }
        }
    }
}
