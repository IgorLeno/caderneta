package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.helpers.NavigationActions
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.fillField
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfiguracoesE2ETest : BaseE2ETest() {
    @Test
    fun salvaPrecosComPromocoesDesativadas() {
        val scenarioName = "config_save"
        launch(scenarioName).use {
            step(scenarioName, "open_ajustes")
            NavigationActions.openAjustes()
            preencherPrecos("5,00", "6,00", "3,00", "4,00")
            screenshot(scenarioName, "before_save")

            step(scenarioName, "save")
            onView(withId(R.id.btn_salvar_configuracoes)).perform(scrollTo(), click())
            onView(withText("Configurações salvas")).check(matches(isDisplayed()))

            WaitConditions.awaitDb {
                app.container.configuracoesRepository
                    .getConfiguracoesOnce()
                    ?.precoSalgadoVistaCentavos == 500L
            }
            val persisted = runBlocking { app.container.configuracoesRepository.getConfiguracoesOnce() }
            assertEquals(600L, persisted?.precoSalgadoPrazoCentavos)
            assertEquals(300L, persisted?.precoSucoVistaCentavos)
            assertEquals(400L, persisted?.precoSucoPrazoCentavos)
            assertEquals(false, persisted?.promocoesAtivadas)
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
        }
    }

    @Test
    fun editaConfiguracoesPersistidas() {
        val scenarioName = "config_edit"
        DatabaseFixture.seedConfiguracoes(app.container)
        launch(scenarioName).use {
            NavigationActions.openAjustes()
            fillField(R.id.et_salgado_vista, "7,50")
            screenshot(scenarioName, "edited_field")
            onView(withId(R.id.btn_salvar_configuracoes)).perform(scrollTo(), click())

            WaitConditions.awaitDb {
                app.container.configuracoesRepository
                    .getConfiguracoesOnce()
                    ?.precoSalgadoVistaCentavos == 750L
            }
            assertEquals(
                750L,
                runBlocking {
                    app.container.configuracoesRepository
                        .getConfiguracoesOnce()
                        ?.precoSalgadoVistaCentavos
                },
            )
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
        }
    }

    @Test
    fun configuracaoInvalidaMostraErroDeCampoENaoPersiste() {
        val scenarioName = "config_invalid"
        launch(scenarioName).use {
            NavigationActions.openAjustes()
            fillField(R.id.et_salgado_vista, "")
            fillField(R.id.et_salgado_prazo, "6,00")
            fillField(R.id.et_suco_vista, "3,00")
            fillField(R.id.et_suco_prazo, "4,00")
            onView(withId(R.id.btn_salvar_configuracoes)).perform(scrollTo(), click())
            screenshot(scenarioName, "validation_error")

            onView(withId(R.id.et_salgado_vista)).check(matches(hasErrorText("Informe o preço à vista")))
            assertEquals(null, runBlocking { app.container.configuracoesRepository.getConfiguracoesOnce() })
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
        }
    }

    private fun preencherPrecos(
        salgadoVista: String,
        salgadoPrazo: String,
        sucoVista: String,
        sucoPrazo: String,
    ) {
        fillField(R.id.et_salgado_vista, salgadoVista)
        fillField(R.id.et_salgado_prazo, salgadoPrazo)
        fillField(R.id.et_suco_vista, sucoVista)
        fillField(R.id.et_suco_prazo, sucoPrazo)
    }
}
