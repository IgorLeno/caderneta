package com.example.caderneta.e2e

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.R
import com.example.caderneta.data.AppDatabase
import com.example.caderneta.helpers.NavigationActions
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.helpers.fillField
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfiguracoesPersistenciaE2ETest : BaseE2ETest() {
    @Test
    fun salvaNoArquivoRoomEReabreTelaComValoresRestaurados() {
        val scenarioName = "config_persistence"
        launch(scenarioName).use {
            NavigationActions.openAjustes()
            fillField(R.id.et_salgado_vista, "5,00")
            fillField(R.id.et_salgado_prazo, "6,00")
            fillField(R.id.et_suco_vista, "3,00")
            fillField(R.id.et_suco_prazo, "4,00")
            onView(withId(R.id.btn_salvar_configuracoes)).perform(scrollTo(), click())
            WaitConditions.awaitDb {
                app.container.configuracoesRepository
                    .getConfiguracoesOnce()
                    ?.precoSalgadoVistaCentavos == 500L
            }
            screenshot(scenarioName, "saved")
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val independentDb = Room.databaseBuilder(context, AppDatabase::class.java, "caderneta_database").build()
        try {
            val persisted = runBlocking { independentDb.configuracoesDao().getConfiguracoesOnce() }
            assertEquals(500L, persisted?.precoSalgadoVistaCentavos)
            assertEquals(600L, persisted?.precoSalgadoPrazoCentavos)
        } finally {
            independentDb.close()
        }

        launch(scenarioName).use {
            NavigationActions.openAjustes()
            onView(withId(R.id.et_salgado_vista)).check(matches(withText("5,00")))
            onView(withId(R.id.et_salgado_prazo)).check(matches(withText("6,00")))
            onView(withId(R.id.et_suco_vista)).check(matches(withText("3,00")))
            onView(withId(R.id.et_suco_prazo)).check(matches(withText("4,00")))
            screenshot(scenarioName, "fresh_activity_restored")
            runBlocking { DatabaseSummaryCollector.write(scenarioName, app.container) }
        }
    }
}
