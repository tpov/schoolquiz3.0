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
        jvmTest.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
    }
}

// Гейт запускает `<модуль>:test`; при старом плагине com.android.library AGP создаёт lifecycle-
// задачу `test` в afterEvaluate — после тела скрипта. Она покрывает unit-тесты вариантов, но не
// jvmTest, а локально доказуемая половина модуля по scope-правилу «код+JVM» (tickets.toml
// epic-2.1) — это именно JVM-тесты. В afterEvaluate добавляем jvmTest к существующему lifecycle.
afterEvaluate {
    tasks.named("test") {
        dependsOn(tasks.named("jvmTest"))
    }
}
