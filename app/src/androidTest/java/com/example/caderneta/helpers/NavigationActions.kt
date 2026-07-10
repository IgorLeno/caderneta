package com.example.caderneta.helpers

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import com.example.caderneta.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.hamcrest.Matcher

object NavigationActions {
    fun openAjustes() {
        select(R.id.configuracoesFragment)
    }

    fun openVendas() {
        select(R.id.vendasFragment)
    }

    fun openConsultas() {
        select(R.id.consultasFragment)
    }

    fun openBalanco() {
        select(R.id.balancoCaixaFragment)
    }

    fun openHistorico() {
        select(R.id.historicoVendasFragment)
    }

    private fun select(itemId: Int) {
        onView(withId(R.id.bottom_navigation)).perform(
            object : ViewAction {
                override fun getConstraints(): Matcher<android.view.View> =
                    isAssignableFrom(BottomNavigationView::class.java)

                override fun getDescription(): String = "select bottom navigation item $itemId"

                override fun perform(
                    uiController: UiController,
                    view: android.view.View,
                ) {
                    (view as BottomNavigationView).selectedItemId = itemId
                    uiController.loopMainThreadUntilIdle()
                }
            },
        )
    }
}
