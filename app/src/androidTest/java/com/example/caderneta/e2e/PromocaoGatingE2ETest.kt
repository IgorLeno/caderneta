package com.example.caderneta.e2e

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.contrib.RecyclerViewActions.actionOnItemAtPosition
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.caderneta.R
import com.example.caderneta.fixtures.DatabaseFixture
import com.example.caderneta.reporting.DatabaseSummaryCollector
import kotlinx.coroutines.runBlocking
import org.hamcrest.Matcher
import org.junit.Assert.assertEquals
import org.junit.Test

class PromocaoGatingE2ETest : BaseE2ETest() {
    @Test
    fun promocaoNaoApareceEmVendasQuandoDesativada() {
        val scenarioName = "promocao_off_hidden"
        DatabaseFixture.seedConfiguracoes(app.container)
        DatabaseFixture.seedClienteBasico(app.container)

        launch(scenarioName).use {
            step(scenarioName, "assert_promocao_hidden")
            onView(withId(R.id.rv_clientes)).perform(
                actionOnItemAtPosition<RecyclerView.ViewHolder>(
                    0,
                    assertChildVisibility(R.id.btnPromocao, View.GONE),
                ),
            )
            screenshot(scenarioName, "vendas_promocao_off")
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
