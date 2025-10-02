plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") // Não precisa de versão aqui, herda do projeto
    id("androidx.navigation.safeargs.kotlin")
    // id("kotlin-kapt") // REMOVIDO COMPLETAMENTE
    id("com.google.devtools.ksp") // APLICAÇÃO DO PLUGIN KSP - ESTA LINHA É CRUCIAL
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.23" // Mantendo a versão atualizada do Kotlin
}

android {
    namespace = "com.example.caderneta"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.caderneta"
        minSdk = 21
        targetSdk = 35 // RECOMENDADO atualizar para corresponder ao compileSdk
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
        compose = true
    }
    composeOptions {
        // Para Kotlin 1.9.24, a compose compiler extension compatível é geralmente 1.5.12 ou superior.
        // Verifique a tabela de compatibilidade: https://developer.android.com/jetpack/androidx/releases/compose-kotlin
        kotlinCompilerExtensionVersion = "1.5.12" // Ou a versão compatível mais recente para Kotlin 1.9.24
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Confirme as versões mais recentes e estáveis para estas BOMs e dependências.
// As versões que você tinha (ex: composeBom = "2025.05.00") eram muito futurísticas.
// Usei exemplos mais próximos da realidade para Maio de 2025, mas você DEVE verificar as últimas estáveis.
val composeBom = "2025.09.01" // Exemplo: Verifique a BOM estável mais recente (ex: lançada em Abril/Maio de 2024 para uso em 2025)
// ou a mais atual no momento que estiver configurando.
val roomVersion = "2.8.1"    // Seu arquivo original tinha Room 2.6.1. Seus logs mostraram download de 2.7.1.
// Escolha UMA e seja consistente. Para este exemplo, vou usar 2.6.1, mas se 2.7.1 é desejado e estável, use-o.
// SE VOCÊ USAR 2.7.1, CERTIFIQUE-SE QUE É UMA VERSÃO ESTÁVEL.
val navVersion = "2.9.5"     // Seu último arquivo build.gradle.kts tinha 2.9.0. Vou usar 2.7.7 que é mais comum para essa época.
// Use a versão estável mais recente.

dependencies {
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")

    implementation("androidx.core:core-ktx:1.17.0") // Exemplo de versão estável (verifique a mais recente)
    implementation("androidx.appcompat:appcompat:1.7.1") // Exemplo de versão estável (verifique a mais recente)
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4") // Exemplo de versão estável (verifique a mais recente)
    implementation("androidx.activity:activity-compose:1.11.0") // Exemplo de versão estável (verifique a mais recente)

    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4") // Exemplo de versão estável

    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended") // Deixe a BOM gerenciar

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    // implementation("androidx.room:room-paging:$roomVersion") // Descomente apenas se usar Room com Paging 3

    // Se libs.filament.android e libs.firebase.vertexai não estiverem definidas em um catálogo de versões (libs.versions.toml)
    // você precisará adicionar as dependências com suas versões explícitas aqui.
    // Exemplo:
    // implementation("com.google.ar.sceneform:filament-android:1.17.1") // Verifique a última versão do Filament
    // implementation("com.google.firebase:firebase-vertexai:XXX") // Verifique o artefato e versão corretos para Vertex AI

    ksp("androidx.room:room-compiler:$roomVersion") // Temporariamente usando implementation em vez de ksp

    implementation("androidx.paging:paging-runtime-ktx:3.3.6") // Exemplo de versão estável (verifique a mais recente)
    // implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0") // Já declarado acima, mantenha uma única declaração consistente
    implementation("androidx.fragment:fragment-ktx:1.8.9") // Exemplo de versão estável (verifique a mais recente)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0") // Exemplo de versão estável (verifique a mais recente)
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.4") // Exemplo de versão estável

    implementation("com.google.android.material:material:1.11.0") // Exemplo de versão estável (verifique a mais recente)

    implementation("androidx.navigation:navigation-fragment-ktx:$navVersion")
    implementation("androidx.navigation:navigation-ui-ktx:$navVersion")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4") // Ou 2.1.5 se for a mais recente que você encontrou

    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test.ext:junit:1.3.0") // Exemplo de versão estável (verifique a mais recente)
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0") // Exemplo de versão estável
    androidTestImplementation(platform("androidx.compose:compose-bom:$composeBom"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.test:runner:1.7.0") // Exemplo de versão estável
    androidTestImplementation("androidx.test:rules:1.7.0") // Exemplo de versão estável
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0") // Corresponder à versão do coroutines-android

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

configurations.all {
    resolutionStrategy {
        force("junit:junit:4.13.2") // Você já tem isso

        // Adicione esta exclusão para as anotações conflitantes
        exclude(group = "com.intellij", module = "annotations")
    }
}

// O bloco kapt { ... } deve ser REMOVIDO se você não estiver usando o plugin kotlin-kapt