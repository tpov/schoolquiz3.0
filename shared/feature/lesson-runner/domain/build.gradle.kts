plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(project(":shared:core:leaderboard"))
            implementation(project(":shared:core:question-schema"))
            implementation(project(":shared:feature:lesson:domain"))
            implementation(project(":shared:feature:question:domain"))
            implementation(project(":shared:feature:app-shell:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":shared:feature:theme:domain"))
        }
    }
}
