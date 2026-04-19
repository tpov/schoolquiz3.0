plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.core.navigation"
}

dependencies {
    api(libs.bundles.decompose)
    implementation(libs.bundles.androidx.ui.base)
    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
}
