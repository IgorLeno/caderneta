package com.example.caderneta.e2e

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.NavigationActions
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Alterna promoções ON -> OFF pela UI garantindo que os dados promocionais (nomes/quantidades/preços)
 * NUNCA são apagados, e que o botão Promoção só aparece em Vendas quando promoções estão ativadas.
 */
class PromocaoOnOffOnE2ETest : BaseE2ETest() {
    @Test
    fun alternaPromocoesPreservandoDadosEGatingEmVendas() {
        val scenarioName = "promocao_on_off"
        DatabaseFixture.seedConfiguracoes(app.container)
        DatabaseFixture.seedClienteBasico(app.container)

        launch(scenarioName).use {
            // Liga promoções e salva.
            step(scenarioName, "ativar_promocoes")
            NavigationActions.openAjustes()
            onView(withId(R.id.switch_promocoes)).perform(scrollTo(), click())
            onView(withId(R.id.btn_salvar_configuracoes)).perform(scrollTo(), click())
            onView(withText("Configurações salvas")).check(matches(isDisplayed()))
            WaitConditions.awaitDb {
                app.container.configuracoesRepository
                    .getConfiguracoesOnce()
                    ?.promocoesAtivadas == true
            }
            val ativada = runBlocking { app.container.configuracoesRepository.getConfiguracoesOnce() }
            assertEquals("Combo Alfa", ativada?.promo1Nome)
            assertEquals(800L, ativada?.promo1PrazoCentavos)

            // Promoção deve aparecer em Vendas.
            step(scenarioName, "promocao_visivel")
            NavigationActions.openVendas()
            onView(withText("Cliente Auditoria")).check(matches(isDisplayed()))
            onView(withId(R.id.rv_clientes)).perform(
                actionOnItemAtPosition<RecyclerView.ViewHolder>(
                    0,
                    assertChildVisibility(R.id.layout_acao_promocao, View.VISIBLE),
                ),
            )
            screenshot(scenarioName, "vendas_promocao_on")

            // Desliga promoções e salva: dados promocionais devem permanecer intactos.
            step(scenarioName, "desativar_promocoes")
            NavigationActions.openAjustes()
            onView(withId(R.id.switch_promocoes)).perform(scrollTo(), click())
            onView(withId(R.id.btn_salvar_configuracoes)).perform(scrollTo(), click())
            onView(withText("Configurações salvas")).check(matches(isDisplayed()))
            WaitConditions.awaitDb {
                app.container.configuracoesRepository
                    .getConfiguracoesOnce()
                    ?.promocoesAtivadas == false
            }
            val desativada = runBlocking { app.container.configuracoesRepository.getConfiguracoesOnce() }
            assertEquals("Combo Alfa", desativada?.promo1Nome)
            assertEquals(1, desativada?.promo1Salgados)
            assertEquals(800L, desativada?.promo1PrazoCentavos)
            assertEquals("Combo Beta", desativada?.promo2Nome)
            assertEquals(1400L, desativada?.promo2PrazoCentavos)
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
        }
    }

    private fun assertChildVisibility(
        childId: Int,
        expectedVisibility: Int,
    ): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = org.hamcrest.Matchers.any(View::class.java)

            override fun getDescription(): String = "assert child $childId visibility is $expectedVisibility"

            override fun perform(
                uiController: UiController,
                view: View,
            ) {
                assertEquals(expectedVisibility, view.findViewById<View>(childId).visibility)
                uiController.loopMainThreadUntilIdle()
            }
        }
}
