plugins {
    id("schoolquiz.android.library")
}

android {
    namespace = "com.tpov.schoolquiz.platform.firebase"
}

dependencies {
    implementation(project(":shared:core:stats"))
    implementation(libs.koin.core)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.firebase.storage.ktx)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.appcheck.play.integrity)
    implementation(libs.play.services.base)
    implementation(libs.play.services.basement)
}
