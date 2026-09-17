package com.tpov.schoolquiz.apps.android_next

import com.tpov.schoolquiz.shared.core.outbox.OutboxOperations
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * У разблокировки урока в маршрутизаторе карантина стоит откат, а не молчание (AD-28).
 *
 * Тест читает исходник composition root, потому что поднять `syncModule` в JVM нечем: ему нужны
 * Android-контекст, WorkManager и живой Firebase. Ловушка при этом не гипотетическая — ровно так
 * эта привязка и жила: `NoLocalEffect` выглядел осознанным решением («локальной половины нет по
 * AD-25»), и никакой тест не мог отличить его от забытого обработчика. Владелец решил 2026-09-03,
 * что локальная половина есть, и молчание здесь означает навсегда открытый урок, за который
 * сервер никогда не списал.
 */
class LessonUnlockQuarantineWiringTest {

    private val syncModuleSource = File("src/main/java/com/tpov/schoolquiz/apps/android_next/di/SyncModule.kt")

    @Test
    fun `lesson unlock quarantine is routed to the rollback, not to silence`() {
        assertTrue("не нашёл ${syncModuleSource.absolutePath}", syncModuleSource.isFile)
        val text = syncModuleSource.readText()

        val binding = text.lineSequence().firstOrNull { "LessonUnlockOutbox.OPERATION to " in it }
        assertTrue(
            "разблокировка урока обязана быть в маршрутизаторе карантина: ${OutboxOperations.UNLOCK_LESSON}",
            binding != null,
        )
        assertTrue(
            "карантин разблокировки обязан звать откат владеющей фичи: $binding",
            binding!!.contains("LessonUnlockQuarantineRollback"),
        )
        assertFalse(
            "«откатывать нечего» здесь неправда: списание и открытие урока локальны (AD-23, AD-28)",
            binding.contains("NoLocalEffect"),
        )
    }
}
