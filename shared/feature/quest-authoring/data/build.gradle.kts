plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.quest_authoring.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(project(":shared:core:catalog:domain"))
            implementation(project(":shared:core:persistence"))
            implementation(project(":shared:core:question-schema"))
            implementation(project(":shared:feature:quest:domain"))
            implementation(project(":shared:feature:quest-authoring:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
