package com.example.caderneta.reporting

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class AuditTestWatcher : TestWatcher() {
    private var lastStep: String = "not-started"
    private val capturedFailures = mutableSetOf<String>()
    val scenarioId: String get() = ScenarioId.current()

    override fun starting(description: Description) {
        ScenarioId.start(description)
        capturedFailures.clear()
        lastStep = "not-started"
    }

    fun step(
        scenario: String,
        step: String,
    ) {
        val scenarioId = ScenarioId.currentOr(scenario)
        lastStep = ScenarioId.stepName(scenario, step)
        LogcatCollector.mark(scenarioId, lastStep)
    }

    override fun failed(
        e: Throwable,
        description: Description,
    ) {
        val scenario = ScenarioId.currentOr(description.methodName ?: description.displayName)
        if (capturedFailures.isNotEmpty()) return
        captureFailure(scenario, e, preClose = false)
    }

    override fun finished(description: Description) {
        ScenarioId.clear()
    }

    fun captureFailure(
        scenario: String,
        throwable: Throwable,
        preClose: Boolean,
    ) {
        val scenarioId = ScenarioId.currentOr(scenario)
        if (!capturedFailures.add(scenarioId)) return
        runCatching { ScreenshotCollector.takeScreenshot(scenarioId, "failure_$lastStep") }
        runCatching { dumpHierarchy(scenarioId) }
        runCatching { DeviceMetadataCollector.writeFailure(scenarioId, lastStep, preClose) }
        runCatching { writeFailure(scenarioId, throwable, preClose) }
    }

    private fun dumpHierarchy(scenario: String) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "$scenario.xml")
        device.dumpWindowHierarchy(file)
        TestOutput.writeBytes("ui-hierarchy/${scenario}_failure.xml", file.readBytes())
    }

    private fun writeFailure(
        scenario: String,
        throwable: Throwable,
        preClose: Boolean,
    ) {
        val trace = StringWriter().also { writer -> throwable.printStackTrace(PrintWriter(writer)) }.toString()
        TestOutput.writeText("failures/${scenario}_failure.txt", "lastStep=$lastStep\npreClose=$preClose\n\n$trace")
    }
}
