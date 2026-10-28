package com.tpov.schoolquiz.apps.android_next

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Цепочка согласия с платными условиями связана насквозь, а не «сама соберётся».
 *
 * Ретро эпика 2 (F17): ни один зелёный тест не разрешал настоящую цепочку согласия — выкинь
 * привязку хранилища согласия из composition root или привязку гейта из модуля экономики, и всё
 * оставалось бы зелёным до первого sync в бою, где это вспыхнуло бы Koin-крахом. Цепочка из
 * четырёх звеньев: хранилище согласия (composition root), очередь-реаниматор `OutboxConsentRequeue`
 * (composition root), гейт `EconomyTermsConsentGate` с реаниматором в конструкторе (модуль
 * экономики), оба consent-ручекни раннеров, читающих гейт (модуль презентации lesson-runner).
 *
 * Тест читает исходники, потому что поднять полный граф в JVM нечем: звену composition root
 * нужны Android-контекст, WorkManager и живой Firebase (прецедент —
 * `LessonUnlockQuarantineWiringTest`); это не разрешение графа, но выкинутое звено больше не
 * проходит незаметно.
 */
class ConsentChainWiringTest {

    @Test
    fun `given the composition root then the consent store and the requeue are bound`() {
        val text = read("src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt")
        assertTrue(
            "хранилище согласия обязано стоять в composition root: single<EconomyTermsConsentStore>",
            text.contains("single<EconomyTermsConsentStore>"),
        )
        assertTrue(
            "реаниматор согласия обязан стоять в composition root: single<OutboxConsentRequeue>",
            text.contains("single<OutboxConsentRequeue>"),
        )
    }

    @Test
    fun `given the economy data module then the gate is bound with the requeue in its constructor`() {
        val text = read("shared/feature/economy/data/src/commonMain/kotlin/com/tpov/schoolquiz/shared/feature/economy/data/di/EconomyDataModule.kt")
        assertTrue(
            "гейт согласия обязан быть привязан в модуле экономики: single<EconomyTermsConsentGate>",
            text.contains("single<EconomyTermsConsentGate>"),
        )
        val binding = text.lineSequence().firstOrNull { "EconomyTermsConsentGateImpl(" in it }
        assertTrue("привязка гейта строит реализацию: $binding", binding != null)
        val body = text.substringAfter("EconomyTermsConsentGateImpl(").lineSequence().take(4).joinToString("\n")
        assertTrue(
            "гейт обязан получить реаниматор очереди (CAP-16): requeue = get()\n$body",
            body.contains("requeue"),
        )
    }

    @Test
    fun `given both runners then their consent handles resolve the real gate`() {
        val text = read(
            "android/feature/lesson-runner/presentation/src/main/kotlin/" +
                "com/tpov/schoolquiz/android/feature/lesson_runner/presentation/di/LessonRunnerPresentationModule.kt",
        )
        val handles = text.lineSequence().filter { "get<EconomyTermsConsentGate>()" in it }.count()
        assertTrue(
            "оба consent-ручекни раннеров обязаны resolving реальный гейт, найдено: $handles",
            handles >= 4,
        )
    }

    private fun read(path: String): File {
        val file = File(path)
        assertTrue("не нашёл ${file.absolutePath}", file.isFile)
        return file
    }
}
