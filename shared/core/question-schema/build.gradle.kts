plugins {
    id("schoolquiz.kmp.library.agp9")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }
        androidMain.dependencies {
            implementation(libs.koin.core)
        }
    }
}
