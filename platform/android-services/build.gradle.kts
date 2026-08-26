plugins {
    id("schoolquiz.android.library")
}

android {
    namespace = "com.tpov.schoolquiz.platform.android_services"
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.work.runtime.ktx)
    implementation(project(":shared:core:sync"))

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.test.ext.junit)
}
