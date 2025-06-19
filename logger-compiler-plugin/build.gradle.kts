@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("jvm-library-publish-convention") // Applies jvm-library-convention (kotlin-jvm, java-library) and maven-publish
    // detekt and ktlint can be applied here or from root
}

// Group and version are essential for publishing.
group = "com.tpov.logger"
version = "1.0.0" // Keep or update as needed
// artifactId will default to project.name ("logger-compiler-plugin") in the publication.

// java sourceCompatibility, targetCompatibility and kotlinOptions.jvmTarget are now set to 17 by convention.
// Kotlin compiler plugin specific configurations (like 'kotlin-dsl' for writing plugins, or specific compiler args)
// would go into the jvm-library-convention or here if highly specific.
// For a compiler plugin, ensuring it's packaged correctly for the Kotlin compiler is key.
// This usually involves having the plugin entry point in META-INF/services.

dependencies {
    // kotlin-stdlib is included by jvm-library-convention
    implementation(libs.kotlin.compiler.embeddable) // Uses version from TOML (kotlin = "1.9.22")
    // Add other necessary dependencies for a compiler plugin, e.g., auto-service if used for service registration.
}

// apply(from = "../publish.gradle.kts") is removed.
// repositories block is removed, handled by settings.gradle.kts.
// Explicit publishing block is removed, handled by jvm-library-publish-convention.