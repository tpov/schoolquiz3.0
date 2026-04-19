plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.app_shell.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:feature:app-shell:domain"))
            implementation(project(":shared:core:stats"))
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
