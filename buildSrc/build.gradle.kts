import org.gradle.kotlin.dsl.`kotlin-dsl`

plugins {
    `kotlin-dsl`
}

// repositories for buildSrc dependencies
repositories {
    google() // For Android Gradle Plugin
    mavenCentral()
    gradlePluginPortal() // For other Gradle plugins if needed by buildSrc
}

// Dependencies needed by buildSrc to compile your convention plugins
dependencies {
    // This is necessary to use types from AGP in your convention plugins
    // Use the same version as your project uses for AGP
    // Note: Accessing libs.versions.toml here is not straightforward during initial buildSrc compilation.
    // It's common to hardcode it or use a version from gradle.properties if shared.
    // For now, hardcoding to the version we have in libs.versions.toml
    implementation("com.android.tools.build:gradle:8.3.1") // Version from libs.versions.toml (agp)

    // If your convention plugins use other specific Gradle plugin APIs, add them here.
    // For example, if you were to configure the KSP plugin directly:
    // implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:1.9.22-1.0.16")
}
