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
            // Воронка магазина — события core, эмитит их расчёт покупки: подтверждение сервера
            // и есть место события завершения, и второго эмиттера у него нет.
            implementation(project(":shared:core:analytics"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
