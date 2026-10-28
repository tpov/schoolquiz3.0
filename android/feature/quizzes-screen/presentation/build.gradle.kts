plugins {
    id("schoolquiz.android.compose.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.quizzes_screen.presentation"
    testOptions {
        unitTests {
            // Robolectric compose tests resolve string resources from the merged resources
            // (NoirArenaEntryContentTest, epic 9.3).
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":android:core:designsystem"))
    implementation(project(":android:feature:lesson-runner:presentation"))
    implementation(project(":shared:feature:quest:domain"))
    implementation(project(":shared:feature:section:domain"))
    implementation(project(":shared:feature:theme:domain"))
    implementation(project(":shared:feature:lesson:domain"))
    implementation(project(":shared:feature:lesson-runner:domain"))
    // Приватная категория (AD-29, история 8.4): список приватных квизов читает сохранённые
    // снимки автора; ключ категории живёт в домене quest-authoring.
    implementation(project(":shared:feature:quest-authoring:domain"))
    // «Начисления» урока (AD-30/UX-20, история 8.6): чтение разрешённой проекции economy.
    implementation(project(":shared:feature:economy:domain"))
    implementation(libs.kotlinx.datetime)
    implementation(project(":shared:feature:question:domain"))
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":shared:feature:economy:domain"))
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
    // Compose-тесты экранов под Robolectric (эпик 9.3) — тот же набор, что у settings-модуля.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(project(":shared:feature:lesson-runner:domain"))
    testImplementation(project(":shared:feature:app-shell:domain"))
    testImplementation(project(":shared:feature:economy:domain"))

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.runner)
    debugImplementation(libs.compose.ui.test.manifest)
}
