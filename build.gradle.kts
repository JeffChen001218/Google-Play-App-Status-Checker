import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    google()
}

dependencies {
    // Note, if you develop a library, you should use compose.desktop.common.
    // compose.desktop.currentOs should be used in launcher-sourceSet
    // (in a separate module for demo project and in testMain).
    // With compose.desktop.common you will also lose @Preview functionality
    implementation(compose.desktop.currentOs)

    // Persistent Settings
    implementation("com.russhwolf:multiplatform-settings:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-no-arg:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-coroutines:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-serialization:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-datastore:1.3.0")
    implementation("com.russhwolf:multiplatform-settings-test:1.3.0")

    // serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // coroutine
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")

    // okhttp
    implementation("io.ktor:ktor-client-core:3.2.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.2.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.2.1")
    implementation("io.ktor:ktor-client-cio:3.2.1")

    // material icon
//    implementation("androidx.compose.material:material-icons-extended:1.7.8")

    // gson
    implementation("com.google.code.gson:gson:2.13.1")
}

compose.desktop {
    application {
        mainClass = "MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "demo"
            packageVersion = "1.0.0"
        }
    }
}

// fix Run Error: Inconsistent JVM-target compatibility detected for tasks 'compileJava' (23) and 'compileKotlin' (22).
java.setTargetCompatibility(22)
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_22
    }
}