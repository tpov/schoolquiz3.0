plugins {
    id("schoolquiz.kmp.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.lesson_runner.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            // UserAnswer is serialized so the chosen answer can be stored with the attempt.
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.core)
            implementation(project(":shared:core:leaderboard"))
            implementation(project(":shared:core:question-schema"))
            // api: Attempt, RunnerState and UserAnswer appear in this module's public signatures,
            // so every consumer of lesson-runner needs these types too.
            api(project(":shared:core:scoring"))
            implementation(project(":shared:feature:lesson:domain"))
            implementation(project(":shared:feature:question:domain"))
            implementation(project(":shared:feature:app-shell:domain"))
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(project(":shared:feature:theme:domain"))
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
