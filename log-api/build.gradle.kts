@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("jvm-library-publish-convention") // Applies jvm-library-convention (kotlin-jvm, java-library) and maven-publish
    // detekt and ktlint can be applied here or from root
}

// Group and version are essential for publishing.
// The convention plugin for jvm library publishing does not set these.
group = "com.tpov.logger" // Standardized group for logger modules
version = "1.0.1"       // Keep or update as needed
// artifactId will default to project.name ("log-api") in the publication if not overridden

// java sourceCompatibility, targetCompatibility and kotlinOptions.jvmTarget are now set to 17 by convention.

// Explicit publishing block is removed, handled by jvm-library-publish-convention.
// The convention creates a "release" publication from the "java" component.

// apply(from = "../publish.gradle.kts") is removed.

// repositories block is removed, handled by settings.gradle.kts.

// No dependencies in this API module.