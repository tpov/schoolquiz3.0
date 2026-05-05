plugins {
    id("schoolquiz.android.library")
}

android {
    namespace = "com.tpov.schoolquiz.platform.firebase"
}

dependencies {
    implementation(project(":shared:core:stats"))
    implementation(project(":shared:core:sync"))
    implementation(project(":shared:core:question-schema"))
    implementation(project(":shared:core:catalog:domain"))
    implementation(project(":shared:core:catalog:data"))
    implementation(project(":shared:feature:quest:data"))
    implementation(project(":shared:feature:quest-authoring:data"))
    implementation(project(":shared:feature:section:data"))
    implementation(project(":shared:feature:theme:data"))
    implementation(project(":shared:feature:lesson:data"))
    implementation(project(":shared:feature:lesson:domain"))
    implementation(project(":shared:core:leaderboard"))
    implementation(project(":shared:feature:question:data"))
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
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}
