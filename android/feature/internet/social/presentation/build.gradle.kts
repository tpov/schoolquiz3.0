plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.internet.social.presentation"
}

dependencies {
    implementation(project(":android:core:designsystem"))
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":shared:feature:internet:social:domain"))
    implementation(project(":shared:feature:internet:social:data"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.decompose)
    implementation(libs.bundles.koin.android)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.androidx.lifecycle.compose)
    implementation(libs.bundles.androidx.ui.base)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.bundles.testing.unit)
}
