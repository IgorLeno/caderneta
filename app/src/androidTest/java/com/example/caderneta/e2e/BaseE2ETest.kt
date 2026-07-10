package com.example.caderneta.e2e

import androidx.test.core.app.ActivityScenario
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.MainActivity
import com.example.caderneta.fixtures.TestStateReset
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.reporting.AuditTestWatcher
import com.example.caderneta.reporting.DeviceMetadataCollector
import com.example.caderneta.reporting.ScreenshotCollector
import org.junit.After
import org.junit.Before
import org.junit.Rule

abstract class BaseE2ETest {
    @get:Rule
    val audit = AuditTestWatcher()

    protected val app: CadernetaApplication get() = TestStateReset.app()

    @Before
    fun baseSetUp() {
        TestStateReset.reset()
        WaitConditions.registerIdlingResource()
    }

    @After
    fun baseTearDown() {
        WaitConditions.unregisterIdlingResource()
    }

    protected fun launch(scenario: String): ActivityScenario<MainActivity> {
        DeviceMetadataCollector.write(scenario)
        return ActivityScenario.launch(MainActivity::class.java)
    }

    protected fun step(
        scenario: String,
        name: String,
    ) {
        audit.step(scenario, name)
    }

    protected fun screenshot(
        scenario: String,
        name: String,
    ): String = ScreenshotCollector.takeScreenshot(scenario, name)
}
