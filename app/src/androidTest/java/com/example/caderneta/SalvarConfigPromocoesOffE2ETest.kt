package com.example.caderneta

import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isNotChecked
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.caderneta.data.entity.Configuracoes
import kotlinx.coroutines.runBlocking
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class SalvarConfigPromocoesOffE2ETest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun salvaPrecosComPromocoesDesativadasEPersisteAposRecriarTela() {
        onView(withText("Ajustes")).perform(click())
        garantirPromocoesDesativadas()

        preencherCampo(R.id.et_salgado_vista, "5,00")
        preencherCampo(R.id.et_salgado_prazo, "6,00")
        preencherCampo(R.id.et_suco_vista, "3,00")
        preencherCampo(R.id.et_suco_prazo, "4,00")

        onView(withId(R.id.btn_salvar_configuracoes)).perform(scrollTo(), click())
        onView(withText("Configurações salvas")).check(matches(withText("Configurações salvas")))

        val persistida = configuracoesPersistidas()
        assertNotNull(persistida)
        assertEquals(500L, persistida?.precoSalgadoVistaCentavos)
        assertEquals(600L, persistida?.precoSalgadoPrazoCentavos)
        assertEquals(300L, persistida?.precoSucoVistaCentavos)
        assertEquals(400L, persistida?.precoSucoPrazoCentavos)
        assertEquals(false, persistida?.promocoesAtivadas)

        activityRule.scenario.recreate()

        onView(withId(R.id.et_salgado_vista)).check(matches(withText("5,00")))
        onView(withId(R.id.et_salgado_prazo)).check(matches(withText("6,00")))
        onView(withId(R.id.et_suco_vista)).check(matches(withText("3,00")))
        onView(withId(R.id.et_suco_prazo)).check(matches(withText("4,00")))
    }

    private fun preencherCampo(
        id: Int,
        valor: String,
    ) {
        onView(withId(id)).perform(scrollTo(), clearText(), replaceText(valor), closeSoftKeyboard())
    }

    private fun garantirPromocoesDesativadas() {
        try {
            onView(withId(R.id.switch_promocoes)).check(matches(isNotChecked()))
        } catch (_: AssertionError) {
            onView(withId(R.id.switch_promocoes)).perform(click())
            onView(withId(R.id.switch_promocoes)).check(matches(not(isChecked())))
        }
    }

    private fun configuracoesPersistidas(): Configuracoes? =
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<CadernetaApplication>()
            app.container.configuracoesRepository.getConfiguracoesOnce()
        }
}
