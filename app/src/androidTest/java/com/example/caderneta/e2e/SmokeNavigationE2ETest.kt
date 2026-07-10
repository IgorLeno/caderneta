package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.NavigationActions
import com.example.caderneta.helpers.fillVisibleField
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SmokeNavigationE2ETest : BaseE2ETest() {
    @Test
    fun navegaPelasCincoAbasERegistraEstadosVisuaisPrincipais() {
        val scenarioName = "smoke_navigation"
        DatabaseFixture.seedConfiguracoes(app.container)
        DatabaseFixture.seedClienteBasico(app.container)

        launch(scenarioName).use {
            onView(withText("Todos os locais")).check(matches(isDisplayed()))
            screenshot(scenarioName, "vendas_filled")

            onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
            screenshot(scenarioName, "vendas_drawer_expanded")
            onView(withText("Audit Local Norte")).perform(click())

            fillVisibleField(R.id.et_busca, "Cliente")
            screenshot(scenarioName, "vendas_keyboard_search")
            onView(withId(R.id.et_busca)).perform(closeSoftKeyboard())

            NavigationActions.openConsultas()
            screenshot(scenarioName, "consultas")
            NavigationActions.openBalanco()
            screenshot(scenarioName, "balanco")
            NavigationActions.openHistorico()
            screenshot(scenarioName, "historico")
            NavigationActions.openAjustes()
            onView(withText("Backup")).check(matches(isDisplayed()))
            screenshot(scenarioName, "ajustes_backup")
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
        }
    }
}
