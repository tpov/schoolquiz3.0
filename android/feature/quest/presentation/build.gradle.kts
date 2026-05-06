plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.quest.presentation"
}

dependencies {
    implementation(project(":shared:feature:quest:domain"))
    implementation(project(":shared:feature:quest-authoring:domain"))
    implementation(project(":shared:feature:economy:domain"))
    implementation(project(":shared:feature:internet:profile:domain"))
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:core:foundation"))
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":android:core:designsystem"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.decompose)
    implementation(libs.bundles.koin.android)
    implementation(libs.bundles.androidx.lifecycle)
    implementation(libs.bundles.androidx.lifecycle.compose)
    implementation(libs.bundles.androidx.ui.base)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.bundles.testing.unit)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.mockito.android)
    debugImplementation(libs.compose.ui.test.manifest)
}
