plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.quest_authoring.presentation"
}

dependencies {
    implementation(project(":android:core:designsystem"))
    implementation(project(":android:feature:lesson-runner:presentation"))
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:core:question-schema"))
    implementation(project(":shared:feature:app-shell:domain"))
    implementation(project(":shared:feature:lesson:domain"))
    implementation(project(":shared:feature:quest:domain"))
    implementation(project(":shared:feature:quest-authoring:domain"))
    implementation(project(":shared:feature:section:domain"))
    implementation(project(":shared:feature:theme:domain"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.serialization.json)

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

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlin.test.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
