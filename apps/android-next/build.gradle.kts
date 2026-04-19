plugins {
    id("schoolquiz.android.compose.application")
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.tpov.schoolquiz.apps.android_next"

    defaultConfig {
        applicationId = "com.tpov.schoolquiz.next"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    // Feature modules
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":shared:feature:app-shell:data"))
    implementation(project(":android:feature:app-shell:presentation"))
    implementation(project(":android:core:navigation"))
    implementation(project(":android:core:designsystem"))
    implementation(project(":platform:firebase"))

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)

    // Decompose
    implementation(libs.bundles.decompose)

    // Koin
    implementation(libs.bundles.koin.android)

    // Lifecycle
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.androidx.lifecycle.compose)

    // Base
    implementation(libs.bundles.androidx.ui.base)

    // Tests
    testImplementation(libs.junit4)
    testImplementation(libs.koin.core)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":shared:core:stats"))
    androidTestImplementation(libs.bundles.testing.instrumented)
}
