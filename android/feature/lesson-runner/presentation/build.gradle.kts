plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.lesson_runner.presentation"
}

dependencies {
    implementation(project(":shared:feature:lesson-runner:domain"))
    implementation(project(":shared:feature:lesson:domain"))
    implementation(project(":shared:feature:internet:profile:domain"))
    // Next-lesson resolution reads lesson.themeId (design decision F3).
    implementation(project(":shared:feature:theme:domain"))
    implementation(project(":shared:core:question-schema"))
    implementation(project(":shared:core:leaderboard"))
    implementation(project(":shared:core:analytics"))
    implementation(project(":android:core:designsystem"))

    implementation(libs.kotlinx.datetime)
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.decompose)
    implementation(libs.bundles.koin.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":shared:feature:theme:domain"))
    testImplementation(project(":shared:feature:question:domain"))
    testImplementation(project(":shared:feature:app-shell:domain"))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
