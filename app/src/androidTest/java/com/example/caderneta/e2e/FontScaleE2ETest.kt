package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.caderneta.R
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.NavigationActions
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.clickRecyclerChild
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FontScaleE2ETest : BaseE2ETest() {
    @Test
    fun fluxosPrincipaisNaoQuebramComFonteAmpliada() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val originalScale = device.currentFontScale()

        DatabaseFixture.seedConfiguracoes(app.container)
        DatabaseFixture.seedClienteBasico(app.container)

        try {
            listOf("1.0", "1.3", "1.5").forEach { scale ->
                val scenarioName = "font_scale_${scale.replace(".", "_")}"
                device.setFontScale(scale)
                device.collapseSystemPanels()

                launch(scenarioName).use {
                    WaitConditions.awaitView(withText("Todos os locais"))
                    screenshot(scenarioName, "vendas_inicial")

                    onView(withId(R.id.drawer_layout)).perform(DrawerActions.open())
                    WaitConditions.awaitView(withText("Audit Local Norte"))
                    screenshot(scenarioName, "drawer")
                    onView(withText("Audit Local Norte")).perform(click())

                    WaitConditions.awaitView(withText("Cliente Auditoria"))
                    clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnVenda)
                    clickRecyclerChild(R.id.rv_clientes, 0, R.id.btnAPrazo)
                    screenshot(scenarioName, "vendas_controles")

                    NavigationActions.openConsultas()
                    WaitConditions.awaitView(withText("Cliente Auditoria"))
                    screenshot(scenarioName, "consultas")

                    NavigationActions.openBalanco()
                    screenshot(scenarioName, "balanco")

                    NavigationActions.openHistorico()
                    screenshot(scenarioName, "historico")

                    NavigationActions.openAjustes()
                    onView(withText("Backup")).check(matches(isDisplayed()))
                    screenshot(scenarioName, "ajustes")

                    runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
                }
            }
        } finally {
            device.setFontScale(originalScale)
        }
    }

    private fun UiDevice.currentFontScale(): String =
        executeShellCommand("settings get system font_scale")
            .trim()
            .takeIf { it.isNotBlank() && it != "null" }
            ?: DEFAULT_FONT_SCALE

    private fun UiDevice.setFontScale(scale: String) {
        executeShellCommand("settings put system font_scale $scale")
        waitForIdle()
    }

    private fun UiDevice.collapseSystemPanels() {
        executeShellCommand("cmd statusbar collapse")
        waitForIdle()
    }

    private companion object {
        const val DEFAULT_FONT_SCALE = "1.0"
    }
}
