package com.example.caderneta.reporting

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File

object ScreenshotCollector {
    fun takeScreenshot(
        scenario: String,
        step: String,
    ): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val device = UiDevice.getInstance(instrumentation)
        device.waitForIdle()
        val safeName = "${scenario}_$step".replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val file = File(instrumentation.targetContext.cacheDir, "$safeName.png")
        check(device.takeScreenshot(file)) { "Unable to capture screenshot $safeName" }
        val outputPath = "screenshots/$safeName.png"
        TestOutput.writeBytes(outputPath, file.readBytes())
        return outputPath
    }
}
