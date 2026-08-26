plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.local.settings.presentation"
    testOptions {
        unitTests {
            // Robolectric compose tests resolve string resources from the merged resources.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":android:core:designsystem"))
    implementation(project(":shared:feature:internet:profile:domain"))
    implementation(project(":shared:core:sync"))
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.androidx.ui.base)
    implementation(libs.bundles.androidx.lifecycle)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
