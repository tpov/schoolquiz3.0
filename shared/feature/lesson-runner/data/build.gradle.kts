@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)

plugins {
    id("schoolquiz.kmp.library")
    alias(libs.plugins.kotlin.serialization)
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
            // Stored answers are serialized alongside the attempt.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(project(":shared:feature:lesson-runner:domain"))
            implementation(project(":shared:feature:lesson:domain"))
            implementation(project(":shared:core:question-schema"))
            implementation(project(":shared:core:persistence"))
            implementation(project(":shared:core:sync"))
            implementation(project(":shared:feature:question:domain"))
        }
        androidMain.dependencies {
            implementation(libs.koin.core)
            implementation(project(":shared:feature:app-shell:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.junit4)
        }
    }
}
