plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.app_shell.presentation"
}

dependencies {
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":android:feature:local:settings:presentation"))
    implementation(project(":android:feature:quest:presentation"))
    implementation(project(":android:feature:quest-authoring:presentation"))
    implementation(project(":android:feature:quizzes-screen:presentation"))
    implementation(project(":shared:feature:quest:domain"))
    implementation(project(":shared:core:foundation"))
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:feature:qualification:domain"))
    implementation(project(":shared:core:sync"))
    implementation(project(":android:core:navigation"))
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
    testImplementation(project(":shared:feature:section:domain"))
    testImplementation(project(":shared:feature:theme:domain"))
    testImplementation(project(":shared:feature:lesson:domain"))
    testImplementation(project(":shared:feature:question:domain"))
    testImplementation(project(":shared:feature:quest-authoring:domain"))
    testImplementation(project(":shared:core:question-schema"))
    testImplementation(project(":shared:feature:lesson-runner:domain"))
    testImplementation(project(":android:feature:lesson-runner:presentation"))
    testImplementation(project(":android:feature:quest:test-fixtures"))

    androidTestImplementation(project(":android:feature:quizzes-screen:presentation"))
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.mockito.android)
    debugImplementation(libs.compose.ui.test.manifest)
}
