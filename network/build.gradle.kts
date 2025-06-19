@Suppress("DSL_SCOPE_VIOLATION") // TODO: Remove once KTIJ-19369 is fixed
plugins {
    id("android-library-convention") // Applies com.android.library, org.jetbrains.kotlin.android, ksp
    alias(libs.plugins.kotlin.kapt)
    // detekt and ktlint are applied from the root project
}

android {
    namespace = "com.tpov.network"
    // compileSdk, minSdk, defaultConfig.testInstrumentationRunner are set by convention.
    // targetSdk is not set for libraries by convention.
    // consumerProguardFiles("consumer-rules.pro") is set by convention.

    // Keep Kapt arguments if Room is used here, or remove if not.
    // Assuming Room might be used due to room-compiler dependency.
    kapt {
        arguments {
            arg("room.schemaLocation", "$projectDir/schemas".toString())
        }
    }

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
    implementation(project(":userguide"))

    implementation(libs.androidx.cardview)
    implementation(libs.androidx.recyclerview)
    implementation(libs.wajahatkarim.easyflipview)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.philjay.mpandroidchart)
    implementation(libs.bumptech.glide)          // Version standardized in TOML

    // Kapt dependencies
    kapt(libs.bumptech.glide.compiler)    // Version standardized in TOML
    kapt(libs.dagger.compiler)          // Version standardized in TOML
    kapt(libs.androidx.room.compiler)
}
