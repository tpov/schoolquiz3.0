plugins {
    id("schoolquiz.android.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.internet.profile.presentation"
}

dependencies {
    implementation(libs.bundles.androidx.ui.base)
    implementation(libs.bundles.androidx.lifecycle)
}
