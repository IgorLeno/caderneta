package com.example.caderneta.e2e

import androidx.test.core.app.ActivityScenario
import com.example.caderneta.CadernetaApplication
import com.example.caderneta.MainActivity
import com.example.caderneta.fixtures.TestStateReset
import com.example.caderneta.helpers.WaitConditions
import com.example.caderneta.reporting.AuditTestWatcher
import com.example.caderneta.reporting.DeviceMetadataCollector
import com.example.caderneta.reporting.ScenarioId
import com.example.caderneta.reporting.ScreenshotCollector
import org.junit.After
import org.junit.Before
import org.junit.Rule
import java.io.Closeable

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

    protected fun launch(scenario: String): AuditActivityScenario {
        val scenarioId = audit.scenarioId
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        DeviceMetadataCollector.write(scenarioId)
        return AuditActivityScenario(
            scenario = scenarioId,
            activityScenario = activityScenario,
            audit = audit,
        )
    }

    protected fun launch(): AuditActivityScenario = launch(audit.scenarioId)

    protected fun step(
        scenario: String,
        name: String,
    ) {
        audit.step(scenario, name)
    }

    protected fun screenshot(
        scenario: String,
        name: String,
    ): String = ScreenshotCollector.takeScreenshot(audit.scenarioId, ScenarioId.stepName(scenario, name))

    protected fun step(name: String) {
        audit.step(audit.scenarioId, name)
    }

    protected fun screenshot(name: String): String = ScreenshotCollector.takeScreenshot(audit.scenarioId, name)
}

class AuditActivityScenario(
    private val scenario: String,
    private val activityScenario: ActivityScenario<MainActivity>,
    private val audit: AuditTestWatcher,
) : Closeable {
    fun recreate() {
        activityScenario.recreate()
    }

    fun <T> use(block: (AuditActivityScenario) -> T): T {
        try {
            return block(this)
        } catch (throwable: Throwable) {
            audit.captureFailure(scenario, throwable, preClose = true)
            throw throwable
        } finally {
            close()
        }
    }

    override fun close() {
        activityScenario.close()
    }
}
