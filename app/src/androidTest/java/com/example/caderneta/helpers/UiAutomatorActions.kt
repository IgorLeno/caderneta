package com.example.caderneta.helpers

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull

private const val UI_TIMEOUT_MS = 5_000L

fun clickByResourceName(resourceName: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    val device = UiDevice.getInstance(instrumentation)
    val target = device.wait(Until.findObject(By.res(packageName, resourceName)), UI_TIMEOUT_MS)
    assertNotNull("Resource not found: $resourceName", target)
    target.click()
    device.waitForIdle()
}

fun waitForResourceName(resourceName: String) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    val device = UiDevice.getInstance(instrumentation)
    val target = device.wait(Until.findObject(By.res(packageName, resourceName)), UI_TIMEOUT_MS)
    assertNotNull("Resource not found: $resourceName", target)
}

fun fillByResourceName(
    resourceName: String,
    value: String,
) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    val device = UiDevice.getInstance(instrumentation)
    val target = device.wait(Until.findObject(By.res(packageName, resourceName)), UI_TIMEOUT_MS)
    assertNotNull("Resource not found: $resourceName", target)
    target.click()
    target.text = value
    device.waitForIdle()
}

fun clickByText(text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val target = device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS)
    assertNotNull("Text not found: $text", target)
    target.click()
    device.waitForIdle()
}

fun clickByAnyText(vararg texts: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val target =
        requireNotNull(
            texts
                .asSequence()
                .mapNotNull { text -> device.wait(Until.findObject(By.text(text)), UI_TIMEOUT_MS / 2) }
                .firstOrNull(),
        ) {
            "Texts not found: ${texts.joinToString()}"
        }
    target.click()
    device.waitForIdle()
}
