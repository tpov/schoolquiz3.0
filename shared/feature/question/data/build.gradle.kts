plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.question.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":shared:feature:question:domain"))
            implementation(project(":shared:feature:lesson:domain"))
            implementation(project(":shared:core:persistence"))
            implementation(project(":shared:core:sync"))
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
