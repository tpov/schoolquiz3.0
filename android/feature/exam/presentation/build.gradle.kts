plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.exam.presentation"
}

dependencies {
    // Сертификаты (CAP-22): транспорт фактов выпуска и публичной верификации.
    implementation(project(":shared:feature:lesson-runner:data"))
    implementation(project(":android:core:designsystem"))

    implementation(libs.kotlinx.datetime)

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.bundles.decompose)
    implementation(libs.bundles.koin.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
