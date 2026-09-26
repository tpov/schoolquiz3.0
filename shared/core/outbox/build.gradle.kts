plugins {
    id("schoolquiz.kmp.library.agp9")
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
        // JVM-only: страж читает functions/config/sync-params.json с диска, а общий код файлов не видит.
        jvmTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
