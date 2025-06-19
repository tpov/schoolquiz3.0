@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("android-library-publish-convention") // Applies com.android.library, org.jetbrains.kotlin.android, ksp, maven-publish
    alias(libs.plugins.kotlin.kapt)
    // detekt and ktlint are applied from the root project if configured there for allprojects/subprojects
}

// Configure publishing details for this specific library
// This will be used by the 'android-library-publish-convention'
group = "com.github.tpov"
version = "1.0.1" // Or your desired version

android {
    namespace = "com.tpov.userguide"
    // compileSdk, minSdk, compileOptions, kotlinOptions, testInstrumentationRunner are set by convention.
    // targetSdk is not usually set in libraries.
    // consumerProguardFiles is set by convention.

    defaultConfig {
        // minSdk is set by convention plugin.
        // targetSdk is not set for libraries by convention plugin.
        consumerProguardFiles("consumer-rules.pro") // Ensure this is still desired if convention sets it
    }

    // buildTypes.release.isMinifyEnabled = false is set by convention.
    // Proguard files for library release are typically consumer-rules.pro.
    // If this module specifically needs to bundle optimized rules, it can be added here.
    // buildTypes {
    //     getByName("release") {
    //         proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    //     }
    // }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat) // Version standardized in TOML
    implementation(libs.google.material)
    implementation(libs.airbnb.lottie)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.kotlin.stdlib) // Already included by convention, but explicit is fine

    // Kapt dependencies
    kapt(libs.dagger.compiler)
    kapt(libs.androidx.room.compiler)

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)

    // AndroidTest dependencies
    androidTestImplementation(libs.androidx.test.ext.junit) // Standardized alias
    androidTestImplementation(libs.androidx.test.espresso.core)
}
