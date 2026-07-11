package com.example.caderneta.reporting

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.caderneta.BuildConfig
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
        val foregroundPackage = runCatching { device.currentPackageName }.getOrDefault("unknown")
        val suffix = if (foregroundPackage == BuildConfig.APPLICATION_ID) "" else "_INVALID"
        val safeName = "${scenario}_$step".replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val outputName = "$safeName$suffix"
        val file = File(instrumentation.targetContext.cacheDir, "$outputName.png")
        check(device.takeScreenshot(file)) { "Unable to capture screenshot $safeName" }
        val outputPath = "screenshots/$outputName.png"
        TestOutput.writeBytes(outputPath, file.readBytes())
        return outputPath
    }
}
