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
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.koin.core)
            implementation(project(":shared:feature:economy:domain"))
            implementation(project(":shared:feature:internet:profile:domain"))
            implementation(project(":shared:core:persistence"))
            // Принятие согласия возвращает ждущие записи очереди в отправку (CAP-16).
            implementation(project(":shared:core:outbox"))
            // Досеттлер просыпается на возвращении связи — знание о ней контракт core.
            implementation(project(":shared:core:network"))
            implementation(project(":shared:core:sync"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            // Тесты сеттлера конструируют расчёт, а тот требует трекер воронки — контракт core.
            implementation(project(":shared:core:analytics"))
        }
    }
}
