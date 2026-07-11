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

configurations.configureEach {
    resolutionStrategy.force("androidx.test.services:storage:1.6.0")
    resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    resolutionStrategy.force("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
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

fun gitDirty(): Boolean =
    try {
        val process =
            ProcessBuilder(listOf("git", "status", "--porcelain"))
                .directory(rootDir)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
        val output =
            process.inputStream
                .bufferedReader()
                .readText()
        process.waitFor() == 0 && output.isNotBlank()
    } catch (_: Exception) {
        true
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
        testInstrumentationRunnerArguments["useTestStorageService"] = "true"
        // e2e.processdeath só roda via scripts/audit/process_death_check.sh (adb shell am
        // instrument direto, sem passar pelos argumentos do Gradle abaixo). Na suite padrão
        // orquestrada pelo Gradle/GMD, ProcessDeathVerifyE2ETest falharia sempre: o Orchestrator
        // limpa os dados do app entre testes, então o estado semeado pela fase anterior nunca
        // chegaria até ele.
        testInstrumentationRunnerArguments["notPackage"] = "com.example.caderneta.e2e.processdeath"
        buildConfigField("String", "GIT_SHA", "\"${gitOutput("rev-parse", "--short", "HEAD")}\"")
        buildConfigField("String", "GIT_SHA_FULL", "\"${gitOutput("rev-parse", "HEAD")}\"")
        buildConfigField("String", "BUILD_TIME", "\"${buildTimeIso()}\"")
        buildConfigField("int", "DB_VERSION", "2")
        buildConfigField("boolean", "GIT_DIRTY", gitDirty().toString())
        buildConfigField("boolean", "IS_AUDIT", "false")
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
        }
        create("audit") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".audit"
            versionNameSuffix = "-audit"
            matchingFallbacks += listOf("debug")
            buildConfigField("boolean", "IS_AUDIT", "true")
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
    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }
    testOptions {
        testBuildType = "audit"
        unitTests.isIncludeAndroidResources = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        managedDevices {
            localDevices {
                create("pixelApi35") {
                    device = "Pixel 7"
                    apiLevel = 35
                    systemImageSource = "aosp"
                }
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.register("testDebugUnitTest") {
    group = "verification"
    description = "Compatibility gate: runs unit tests for the audit test build type."
    dependsOn("testAuditUnitTest")
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
    implementation(libs.androidx.exifinterface)

    implementation(libs.androidx.espresso.idling.resource)

    implementation(libs.material)
    implementation(libs.mpandroidchart)
    implementation(libs.coil)

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
    androidTestImplementation(libs.androidx.espresso.contrib)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.storage)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestUtil(libs.androidx.test.orchestrator)
    androidTestUtil(libs.androidx.test.services)
}
