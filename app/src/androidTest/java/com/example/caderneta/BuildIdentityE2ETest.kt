package com.example.caderneta

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BuildIdentityE2ETest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun configuracoesExibeIdentidadeDoBuildAtual() {
        assertNotEquals("unknown", BuildConfig.GIT_SHA)

        onView(withText("Ajustes")).perform(click())

        onView(withId(R.id.tv_build_info))
            .check(matches(withText(allOf(containsString("Caderneta ${BuildConfig.VERSION_NAME}")))))
            .check(matches(withText(containsString("Código ${BuildConfig.VERSION_CODE}"))))
            .check(matches(withText(containsString("Build ${BuildConfig.BUILD_TYPE}"))))
            .check(matches(withText(containsString("Commit ${BuildConfig.GIT_SHA}"))))
            .check(matches(withText(not(containsString("Commit unknown")))))
            .check(matches(withText(containsString("Banco ${BuildConfig.DB_VERSION}"))))
    }
}
