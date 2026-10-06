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

// Гейт запускает `<модуль>:test`, но в KMP-модуле с android- и jvm-целями точной задачи «test»
// нет, и голое имя неоднозначно (расширяется в testAndroid / testAndroidHostTest). Точное имя
// побеждает расширение имён, поэтому регистрируем его: локально доказуемая половина модуля —
// JVM-тесты (jvmTest; прогон 2.2 — 120 записей и ветви CAP-18 — живёт именно там, tickets.toml
// epic-2.2); device-тесты вне scope-правила «код+JVM».
tasks.register("test") {
    group = "verification"
    description = "JVM-тесты модуля (jvmTest) — локально доказуемая половина по scope-правилу."
    dependsOn(tasks.named("jvmTest"))
}
