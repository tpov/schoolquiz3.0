plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.core.outbox"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            api(project(":shared:core:network"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        // JVM-only: страж читает config/sync-params.json с диска, а общий код файлов не видит.
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
