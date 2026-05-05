plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.core.designsystem"
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.bundles.compose.ui)
    api(libs.compose.ui.tooling.preview)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:feature:quest:domain"))
    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
