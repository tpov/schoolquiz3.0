package com.tpov.schoolquiz.apps.android_next

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * У ready-ACK в маршрутизаторе карантина стоит честное «локальной половины нет», а не молчание.
 *
 * Ретро эпика 2 (F15): READY_ACK объявлен операцией с постоянной квитанцией, но в карте
 * маршрутизатора карантина строки за ним не стояло — закарантиненный ready-ACK уходил в
 * log-only `onUnhandled`, и «нечего откатывать» нельзя было отличить от «забыли обработчика»
 * (AD-28). У ready-ACK локальной половины правда нет: квитанция живёт в хранилище квитанций,
 * на серверные поля она не посягает, — но объявлено это должно быть явно, `NoLocalEffect`.
 *
 * Тест читает исходник composition root, потому что поднять `syncModule` в JVM нечем: ему нужны
 * Android-контекст, WorkManager и живой Firebase. Образец — `LessonUnlockQuarantineWiringTest`.
 */
class ReadyAckQuarantineWiringTest {

    private val syncModuleSource = File("src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt")

    @Test
    fun `ready-ack quarantine is routed as an explicit no-local-effect, not to silence`() {
        assertTrue("не нашёл ${syncModuleSource.absolutePath}", syncModuleSource.isFile)
        val text = syncModuleSource.readText()

        val binding = text.lineSequence().firstOrNull { "OutboxOperations.READY_ACK to " in it }
        assertTrue(
            "ready-ack обязан стоять в карте маршрутизатора карантина: ${OutboxOperations.READY_ACK}",
            binding != null,
        )
        assertTrue(
            "у ready-ACK локальной половины нет — обработчик объявляет это явно (NoLocalEffect): $binding",
            binding!!.contains("NoLocalEffect"),
        )
    }
}
