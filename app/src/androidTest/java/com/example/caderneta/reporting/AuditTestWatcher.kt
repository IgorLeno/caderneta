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

    fun step(
        scenario: String,
        step: String,
    ) {
        lastStep = step
        LogcatCollector.mark(scenario, step)
    }

    override fun failed(
        e: Throwable,
        description: Description,
    ) {
        val scenario = description.methodName ?: description.displayName
        if (capturedFailures.isNotEmpty()) return
        captureFailure(scenario, e, preClose = false)
    }

    fun captureFailure(
        scenario: String,
        throwable: Throwable,
        preClose: Boolean,
    ) {
        if (!capturedFailures.add(scenario)) return
        runCatching { ScreenshotCollector.takeScreenshot(scenario, "failure_$lastStep") }
        runCatching { dumpHierarchy(scenario) }
        runCatching { DeviceMetadataCollector.writeFailure(scenario, lastStep, preClose) }
        runCatching { writeFailure(scenario, throwable, preClose) }
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
