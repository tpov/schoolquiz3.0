package com.tpov.schoolquiz.apps.android_next

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Расписание фоновой синхронизации ставит только планировщик.
 *
 * Приложение планировало периодическую синхронизацию само, мимо `SyncScheduler`: `KEEP` с зашитыми
 * сутками, на том же уникальном имени работы, которым распоряжается `applyFrequency`. Для каденций
 * с интервалом это значило лишь, что первым словом было чужое. Для «вручную» и «при запуске»
 * интервала нет, и планировщик такую работу снимает — а приложение сажало её обратно при каждом
 * старте процесса, включая старт от самого WorkManager, когда снять её уже некому.
 *
 * Проверяется текстом по исходникам, потому что защищается отсутствие кода, а не поведение класса:
 * `Application` поднимается системой, и написать на неё обычный тест нельзя.
 */
class SyncScheduleOwnershipTest {

    private val appSources = File("src/main/java")

    /** Всё, чем ставят и снимают работу WorkManager. */
    private val schedulingCalls =
        listOf(
            "enqueueUniquePeriodicWork",
            "enqueueUniqueWork",
            "cancelUniqueWork",
            "PeriodicWorkRequestBuilder",
            "OneTimeWorkRequestBuilder",
        )

    @Test
    fun `given the app module then nothing outside the scheduler enqueues background work`() {
        assertTrue("не нашёл исходники: ${appSources.absolutePath}", appSources.isDirectory)

        val offenders =
            appSources.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    val text = file.readText()
                    schedulingCalls.filter { text.contains(it) }.map { "${file.name}: $it" }
                }
                .toList()

        assertEquals(
            "расписание ставится мимо SyncScheduler:\n" + offenders.joinToString("\n"),
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `given the app then it reconciles the schedule at process start, not only on screen`() {
        // Процесс поднимается и без Activity — WorkManager будит его сам. Сверка расписания,
        // живущая только в MainActivity, при таком старте не случается вовсе.
        val application = File(appSources, "com/tpov/schoolquiz/apps/android_next/AppApplication.kt").readText()

        assertTrue(
            "AppApplication обязано сверять обе каденции на старте процесса",
            application.contains("applyFrequency(") && application.contains("applyProfileFrequency("),
        )
    }
}
