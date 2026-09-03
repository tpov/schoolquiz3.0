plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.economy.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)
            // Знание о связи и таксономия ошибок обращения — контракты core, не чужая фича:
            // покупка обязана отказать до открытия Play, назвав причину (SyncError.NoNetwork).
            implementation(project(":shared:core:network"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
