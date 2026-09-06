plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.app_shell.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(project(":shared:core:foundation"))
            // Снимок состояния синхронизации стоит в сигнатуре RootComponent — тип должен быть
            // виден всем, кто этот интерфейс читает.
            api(project(":shared:core:sync"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
