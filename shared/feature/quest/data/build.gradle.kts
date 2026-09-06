plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.quest.data"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            // Время постановки в очередь — от общих часов, тело записи — строкой JSON (ядро в него не смотрит).
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(project(":shared:feature:quest:domain"))
            implementation(project(":shared:core:catalog:domain"))
            implementation(project(":shared:core:catalog:data"))
            implementation(project(":shared:core:persistence"))
            implementation(project(":shared:core:sync"))
            implementation(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
