plugins {
    id("schoolquiz.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.lesson.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // Время постановки в очередь — от общих часов, тело записи — строкой JSON.
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared:feature:lesson:domain"))
            implementation(project(":shared:feature:theme:domain"))
            implementation(project(":shared:core:persistence"))
            implementation(project(":shared:core:sync"))
            implementation(project(":shared:core:leaderboard"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
