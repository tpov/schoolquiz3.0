plugins {
    id("schoolquiz.android.compose.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.quizzes_screen.presentation"
}

dependencies {
    implementation(project(":android:core:designsystem"))
    implementation(project(":android:feature:lesson-runner:presentation"))
    implementation(project(":shared:feature:quest:domain"))
    implementation(project(":shared:feature:section:domain"))
    implementation(project(":shared:feature:theme:domain"))
    implementation(project(":shared:feature:lesson:domain"))
    implementation(project(":shared:feature:lesson-runner:domain"))
    implementation(project(":shared:feature:question:domain"))
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:core:question-schema"))
    implementation(project(":shared:core:sync"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.decompose)
    implementation(libs.bundles.koin.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(project(":shared:feature:lesson-runner:domain"))
    testImplementation(project(":shared:feature:app-shell:domain"))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    debugImplementation(libs.compose.ui.test.manifest)
}
