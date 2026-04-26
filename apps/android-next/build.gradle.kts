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
    implementation(project(":android:feature:quest:presentation"))
    implementation(project(":android:feature:quizzes-screen:presentation"))
    implementation(project(":android:core:navigation"))
    implementation(project(":android:core:designsystem"))
    implementation(project(":platform:firebase"))
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(project(":shared:core:persistence"))
    implementation(project(":shared:core:catalog:data"))
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:feature:quest:data"))
    implementation(project(":shared:feature:quest:domain"))
    implementation(project(":shared:feature:section:data"))
    implementation(project(":shared:feature:section:domain"))
    implementation(project(":shared:feature:theme:data"))
    implementation(project(":shared:feature:theme:domain"))
    implementation(project(":shared:feature:lesson:data"))
    implementation(project(":shared:feature:lesson:domain"))
    implementation(project(":shared:feature:question:data"))
    implementation(project(":shared:feature:question:domain"))
    implementation(project(":platform:android-services"))
    implementation(libs.androidx.work.runtime.ktx)
    implementation(project(":shared:core:sync"))

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
    testImplementation(libs.mockito.core)
    testImplementation(project(":shared:core:stats"))
    androidTestImplementation(libs.bundles.testing.instrumented)
}
