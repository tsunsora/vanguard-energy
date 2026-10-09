plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val customKey = providers.environmentVariable("ENERGY_KEYSTORE_PATH").orNull
if (customKey != null) {
    require(listOf("ENERGY_KEYSTORE_PASSWORD", "ENERGY_KEY_ALIAS", "ENERGY_KEY_PASSWORD")
        .all { !System.getenv(it).isNullOrBlank() }) { "Set all ENERGY_KEYSTORE signing variables." }
}
android {
    namespace = "app.vanguard.energy"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.vanguard.energy"
        minSdk = 24
        targetSdk = 36
        versionCode = 26
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("distribution") {
            // Keep the original sideload identity. Gradle does not generate this key.
            storeFile = file(customKey ?: "${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = System.getenv("ENERGY_KEYSTORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("ENERGY_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("ENERGY_KEY_PASSWORD") ?: "android"
        }
    }
    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("distribution")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    lint { abortOnError = true }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
dependencies {
    implementation(project(":shared"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.compose.material3:material3:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4:1.10.5")
    debugImplementation("androidx.compose.ui:ui-test-manifest:1.10.5")
}
