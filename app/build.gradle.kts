import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Read the Claude API key from local.properties so it never lives in source code.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val anthropicKey: String = localProps.getProperty("ANTHROPIC_API_KEY", "")

android {
    namespace = "com.elite.assistant"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elite.assistant"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Exposed to Kotlin as BuildConfig.ANTHROPIC_API_KEY
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"$anthropicKey\"")
    }

    buildTypes {
        release {
            // Kept off: the Anthropic SDK uses Jackson reflection, which R8 would strip.
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        // The SDK's dependencies ship duplicate license files; drop them.
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/INDEX.LIST",
            "META-INF/*.kotlin_module",
            "META-INF/versions/9/previous-compilation-data.bin",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // Official Anthropic SDK (Java, works from Kotlin) for talking to Claude.
    implementation("com.anthropic:anthropic-java:2.34.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
