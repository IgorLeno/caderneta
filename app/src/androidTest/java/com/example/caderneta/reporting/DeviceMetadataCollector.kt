package com.example.caderneta.reporting

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.example.caderneta.BuildConfig
import org.json.JSONObject
import java.util.Locale

object DeviceMetadataCollector {
    fun write(scenario: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val metrics = context.resources.displayMetrics
        val config = context.resources.configuration
        val json =
            JSONObject()
                .put("scenario", scenario)
                .put("manufacturer", Build.MANUFACTURER)
                .put("model", Build.MODEL)
                .put("api", Build.VERSION.SDK_INT)
                .put("instrumentationPackage", InstrumentationRegistry.getInstrumentation().context.packageName)
                .put("resolution", "${metrics.widthPixels}x${metrics.heightPixels}")
                .put("density", metrics.density)
                .put("locale", Locale.getDefault().toLanguageTag())
                .put("fontScale", config.fontScale.toDouble())
                .put("nightMode", config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("buildType", BuildConfig.BUILD_TYPE)
                .put("isAudit", BuildConfig.IS_AUDIT)
                .put("gitDirty", BuildConfig.GIT_DIRTY)
                .put("gitSha", BuildConfig.GIT_SHA)
                .put("gitShaFull", BuildConfig.GIT_SHA_FULL)
                .put("buildTime", BuildConfig.BUILD_TIME)
                .put("dbVersion", BuildConfig.DB_VERSION)
                .put("foregroundPackage", foregroundPackage())
                .put("foregroundActivity", foregroundActivity())
        TestOutput.writeText("metadata/${scenario}_device_build.json", json.toString(2))
    }

    fun writeFailure(
        scenario: String,
        lastStep: String,
        preClose: Boolean,
    ) {
        val json =
            JSONObject()
                .put("scenario", scenario)
                .put("lastStep", lastStep)
                .put("preClose", preClose)
                .put("expectedPackage", BuildConfig.APPLICATION_ID)
                .put("foregroundPackage", foregroundPackage())
                .put("foregroundActivity", foregroundActivity())
        TestOutput.writeText("metadata/${scenario}_failure_device_build.json", json.toString(2))
    }

    fun foregroundPackage(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return runCatching {
            UiDevice.getInstance(instrumentation).currentPackageName
        }.getOrDefault("unknown")
    }

    fun foregroundActivity(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        return runCatching {
            val device = UiDevice.getInstance(instrumentation)
            val output =
                device.executeShellCommand(
                    "sh -c \"dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -n 2\"",
                )
            output.trim().ifBlank { foregroundPackage() }
        }.getOrDefault("unknown")
    }
}
