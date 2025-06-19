@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("android-library-convention") // Applies com.android.library, org.jetbrains.kotlin.android, ksp
    // No kapt needed based on original file
    // detekt and ktlint are applied from the root project
}

android {
    namespace = "com.tpov.core"
    // compileSdk, minSdk (defaults to 26), defaultConfig.testInstrumentationRunner,
    // consumerProguardFiles, compileOptions (Java 17), kotlinOptions (JVM 17) are set by convention.

    // If minSdk 28 is strictly required for this module, uncomment and set it:
    // defaultConfig {
    //     minSdk = 28
    // }

    // buildTypes.release.isMinifyEnabled = false is set by convention.
    // Proguard rules for library release are typically consumer-rules.pro.
    // If this module specifically needs to bundle optimized rules:
    // buildTypes {
    //     getByName("release") {
    //         proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    //     }
    // }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)    // Version standardized in TOML (1.7.0)
    implementation(libs.google.material)

    // Test dependencies
    testImplementation(libs.junit)

    // AndroidTest dependencies
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}