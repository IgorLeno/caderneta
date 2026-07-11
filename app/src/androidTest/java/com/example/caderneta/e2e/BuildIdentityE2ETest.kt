package com.example.caderneta.e2e

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import com.example.caderneta.BuildConfig
import com.example.caderneta.R
import com.example.caderneta.helpers.NavigationActions
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertNotEquals
import org.junit.Test

class BuildIdentityE2ETest : BaseE2ETest() {
    @Test
    fun configuracoesExibeIdentidadeDoBuildAtual() {
        val scenarioName = "build_identity"
        launch(scenarioName).use {
            step(scenarioName, "open_ajustes")
            NavigationActions.openAjustes()
            screenshot(scenarioName, "ajustes_build_identity")

            assertNotEquals("unknown", BuildConfig.GIT_SHA)
            onView(withId(R.id.tv_build_info))
                .check(matches(isDisplayed()))
                .check(matches(withText(allOf(containsString("Caderneta ${BuildConfig.VERSION_NAME}")))))
                .check(matches(withText(containsString("Código ${BuildConfig.VERSION_CODE}"))))
                .check(matches(withText(containsString("Build ${BuildConfig.BUILD_TYPE}"))))
                .check(matches(withText(containsString("Audit ${BuildConfig.IS_AUDIT}"))))
                .check(matches(withText(containsString("Dirty ${BuildConfig.GIT_DIRTY}"))))
                .check(matches(withText(containsString("Commit ${BuildConfig.GIT_SHA}"))))
                .check(matches(withText(not(containsString("Commit unknown")))))
                .check(matches(withText(containsString("Banco ${BuildConfig.DB_VERSION}"))))
        }
    }
}
