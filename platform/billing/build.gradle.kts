plugins {
    id("schoolquiz.android.library")
}

android {
    namespace = "com.tpov.schoolquiz.platform.billing"
}

dependencies {
    // The contract this module implements. Play types never cross back the other way.
    implementation(project(":shared:feature:economy:domain"))
    implementation(project(":shared:core:analytics"))
    implementation(libs.play.billing.ktx)
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.bundles.kotlinx.coroutines)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
