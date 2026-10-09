import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidLibrary {
        namespace = "app.vanguard.energy.shared"
        compileSdk = 36
        minSdk = 24
        androidResources { enable = true }
        compilerOptions { jvmTarget = JvmTarget.JVM_17 }
    }
    jvm { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "EnergyShared"
            isStatic = true
            binaryOption("bundleId", "app.vanguard.energy.shared")
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:1.10.3")
            implementation("org.jetbrains.compose.foundation:foundation:1.10.3")
            implementation("org.jetbrains.compose.material3:material3:1.9.0")
            implementation("org.jetbrains.compose.ui:ui:1.10.3")
            implementation("org.jetbrains.compose.components:components-resources:1.10.3")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        // Renders the shared UI on the desktop JVM so layout and gestures are checked without a device.
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.ui:ui-test:1.10.3")
        }
    }
}
compose.resources { packageOfResClass = "app.vanguard.energy.resources" }
