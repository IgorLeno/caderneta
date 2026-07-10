import java.time.Instant

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigation.safeargs)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
}

detekt {
    config.setFrom(rootProject.file("detekt-config.yml"))
    baseline = file("detekt-baseline.xml")
    buildUponDefaultConfig = true
}

ktlint {
    android.set(true)
    baseline.set(file("ktlint-baseline.xml"))
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

fun gitOutput(vararg args: String): String =
    try {
        val process =
            ProcessBuilder(listOf("git", *args))
                .directory(rootDir)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        val output =
            process.inputStream
                .bufferedReader()
                .readText()
                .trim()
        if (process.waitFor() == 0) output.ifBlank { "unknown" } else "unknown"
    } catch (_: Exception) {
        "unknown"
    }

fun buildTimeIso(): String {
    val sourceDateEpoch = providers.environmentVariable("SOURCE_DATE_EPOCH").orNull?.toLongOrNull()
    return Instant.ofEpochSecond(sourceDateEpoch ?: Instant.now().epochSecond).toString()
}

android {
    namespace = "com.example.caderneta"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.caderneta"
        minSdk = 23
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
        buildConfigField("String", "GIT_SHA", "\"${gitOutput("rev-parse", "--short", "HEAD")}\"")
        buildConfigField("String", "GIT_SHA_FULL", "\"${gitOutput("rev-parse", "HEAD")}\"")
        buildConfigField("String", "BUILD_TIME", "\"${buildTimeIso()}\"")
        buildConfigField("int", "DB_VERSION", "1")
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.material)
    implementation(libs.mpandroidchart)

    implementation(libs.kotlinx.coroutines.android)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.core.ktx)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.org.json)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestUtil(libs.androidx.test.orchestrator)
}
