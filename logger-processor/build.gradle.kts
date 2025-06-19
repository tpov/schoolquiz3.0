@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("jvm-library-publish-convention") // Applies jvm-library-convention (kotlin-jvm, java-library) and maven-publish
    // detekt and ktlint can be applied here or from root
    // If this is an annotation processor using KAPT, kotlin-kapt plugin would be needed.
    // If it's a KSP processor, specific KSP setup would be needed.
    // Assuming it's a standard Java annotation processor for now.
}

// Group and version are essential for publishing.
group = "com.tpov.logger"
version = "1.0.0" // Keep or update as needed
// artifactId will default to project.name ("logger-processor") in the publication.

// java sourceCompatibility, targetCompatibility and kotlinOptions.jvmTarget are now set to 17 by convention.

dependencies {
    // kotlin-stdlib is included by jvm-library-convention
    implementation(libs.kotlin.stdlib) // Standardized version from TOML
    implementation(project(":log-api"))

    // For annotation processors, you often need:
    // compileOnlyOnly("com.google.auto.service:auto-service:1.1.1") // Check for latest version
    // kapt("com.google.auto.service:auto-service:1.1.1") // If using Kapt to process annotations in this module itself
    // implementation("com.squareup:kotlinpoet:1.16.0") // Check for latest version, if generating Kotlin code
    // These are not in the original, so not adding them now unless requested.
}

// apply(from = "../publish.gradle.kts") is removed.
// repositories block is removed, handled by settings.gradle.kts.