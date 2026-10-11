plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.internet.social.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            // Тела публикации и голоса собираются здесь и уезжают строкой: ядро в них не смотрит.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(project(":shared:core:outbox"))
            implementation(project(":shared:feature:internet:social:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
