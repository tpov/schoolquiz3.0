@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("android-library-convention") // Applies com.android.library, org.jetbrains.kotlin.android, ksp
    alias(libs.plugins.kotlin.kapt)
    // detekt and ktlint are applied from the root project
}

android {
    namespace = "com.tpov.setting" // "com.tpov.setting" or "com.tpov.settings"? Original was "com.tpov.setting"
    // compileSdk, minSdk, defaultConfig.testInstrumentationRunner are set by convention.
    // targetSdk is not set for libraries by convention.
    // consumerProguardFiles("consumer-rules.pro") is set by convention.

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true // Override convention plugin's default (false for libraries)
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)    // Version standardized in TOML (1.7.0)
    implementation(libs.google.material)
    implementation(libs.androidx.preference.ktx)

    // Kapt dependencies
    kapt(libs.dagger.compiler)      // Version standardized in TOML (2.49)
    kapt(libs.androidx.room.compiler)

    // Test dependencies
    testImplementation(libs.junit)

    // AndroidTest dependencies
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
}
