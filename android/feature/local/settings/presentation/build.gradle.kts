plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.local.settings.presentation"
}

dependencies {
    implementation(project(":android:core:designsystem"))
    implementation(project(":shared:feature:internet:profile:domain"))
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.androidx.ui.base)
    implementation(libs.bundles.androidx.lifecycle)
    debugImplementation(libs.compose.ui.tooling)
}
