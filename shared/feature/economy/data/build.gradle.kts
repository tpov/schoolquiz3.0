plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.economy.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            implementation(project(":shared:feature:economy:domain"))
            implementation(project(":shared:feature:internet:profile:domain"))
            implementation(project(":shared:core:persistence"))
            // Досеттлер просыпается на возвращении связи — знание о ней контракт core.
            implementation(project(":shared:core:network"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
