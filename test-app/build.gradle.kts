@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("jvm-application-convention") // Applies org.jetbrains.kotlin.jvm and application
    alias(libs.plugins.tpov.logger)
    // detekt and ktlint can be applied here or from root
}

// java sourceCompatibility, targetCompatibility and kotlinOptions.jvmTarget are now set to 17 by convention.

application {
    mainClass.set("com.tpov.testapp.MainKt") // Specific to this application
}

// repositories block is removed, handled by settings.gradle.kts.

dependencies {
    // kotlin-stdlib is included by jvm-application-convention
    implementation(libs.kotlin.stdlib) // Standardized version from TOML (replaces kotlin-stdlib-jdk8)
    implementation(project(":log-api"))
} 