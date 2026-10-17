plugins {
    id("schoolquiz.android.compose.library")
}

android {
    namespace = "com.tpov.schoolquiz.android.feature.tournament.presentation"
}

dependencies {
    implementation(project(":android:core:designsystem"))
    implementation(project(":shared:feature:tournament:domain"))
    implementation(project(":shared:feature:tournament:data"))
    implementation(project(":shared:core:outbox"))
    implementation(project(":shared:core:network"))
    // Замороженный момент согласия показывается той же формой времени, что шторка и настройки.
    implementation(project(":shared:core:sync"))

    implementation(platform(libs.compose.bom))
    implementation(libs.bundles.compose.ui)
    implementation(libs.bundles.decompose)
    implementation(libs.bundles.koin.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":shared:feature:tournament:domain"))
    testImplementation(project(":shared:feature:tournament:data"))
    testImplementation(project(":shared:core:outbox"))
}
