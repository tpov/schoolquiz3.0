@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("gradle-plugin-publish-convention") // Applies org.jetbrains.kotlin.jvm, java-gradle-plugin, maven-publish
    // detekt and ktlint can be applied here if desired for this module specifically,
    // or rely on root project's allprojects/subprojects configuration.
}

// Group and version are essential for publishing and should be defined here.
// The convention plugin for gradle plugins does not set these.
group = "com.tpov.logger"
version = "1.0.0" // Keep this version or update as needed

// Repositories block is removed, should be handled by settings.gradle.kts

dependencies {
    // gradleApi() is provided by "java-gradle-plugin"
    // kotlin("stdlib") is provided by "org.jetbrains.kotlin.jvm" which is applied by the convention plugin
    // So, these can often be omitted if the convention plugin includes them, but explicit is also fine.
    implementation(gradleApi()) // Equivalent to kotlin("gradle-plugin-api") or older gradleapi()
    implementation(libs.kotlin.stdlib)
    compileOnly(libs.kotlin.gradle.plugin.artifact) // org.jetbrains.kotlin:kotlin-gradle-plugin
    implementation(libs.kotlin.compiler.embeddable)
}

// gradlePlugin block remains, as it's specific to this plugin module
gradlePlugin {
    plugins {
        create("loggerGradlePlugin") {
            id = "com.tpov.logger.gradle-plugin" // This ID should match what users will apply
            implementationClass = "com.tpov.logger_gradle_plugin.LoggerGradlePlugin"
            displayName = "Logger Gradle Plugin" // Optional: Add a display name
            description = "A Gradle plugin for custom logging." // Optional: Add a description
        }
    }
}

// The apply(from = "../publish.gradle.kts") is removed.
// Publishing configuration is now handled by the 'gradle-plugin-publish-convention'.
// The convention plugin sets up mavenLocal() by default.
// If other repositories are needed for publishing this plugin,
// they should be configured in the root build.gradle.kts or directly here if specific.
// For example, to publish to Gradle Plugin Portal, specific configurations would be needed.