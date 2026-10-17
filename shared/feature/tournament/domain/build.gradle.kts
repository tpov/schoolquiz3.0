plugins {
    id("schoolquiz.kmp.library")
}

android {
    namespace = "com.tpov.schoolquiz.shared.feature.tournament.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
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
