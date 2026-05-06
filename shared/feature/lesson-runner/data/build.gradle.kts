@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.data"
}

kotlin {
    applyDefaultHierarchyTemplate {
        common {
            group("jvmAndAndroid") {
                withAndroidTarget()
                withJvm()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared:feature:lesson-runner:domain"))
            implementation(project(":shared:feature:lesson:domain"))
            implementation(project(":shared:core:question-schema"))
            implementation(project(":shared:core:persistence"))
            implementation(project(":shared:core:sync"))
        }
        androidMain.dependencies {
            implementation(libs.koin.core)
            implementation(project(":shared:feature:question:domain"))
            implementation(project(":shared:feature:app-shell:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.junit4)
        }
    }
}
