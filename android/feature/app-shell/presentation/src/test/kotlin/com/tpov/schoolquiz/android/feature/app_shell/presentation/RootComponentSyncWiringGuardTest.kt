package com.tpov.schoolquiz.android.feature.app_shell.presentation

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Страж проводки состояния синхронизации в корень (AD-14).
 *
 * Пока у параметра стояло значение по умолчанию — заглушка с вечными нулями, — забытая привязка не
 * ломала ни сборку, ни тесты: экран молча показывал «синхронизации ещё не было», то есть ровно то
 * молчание, ради устранения которого состояние и выводится наружу. Так и случилось: привязки в
 * графе не было, а заметил это только разбор эпика.
 *
 * Читается исходник, потому что проверяемое свойство — отсутствие умолчания в объявлении, а не
 * поведение: любое поведение у заглушки правдоподобно, тем она и опасна.
 */
class RootComponentSyncWiringGuardTest {

    private val source: String by lazy {
        val file =
            File(
                "src/main/kotlin/com/tpov/schoolquiz/android/feature/app_shell/presentation/" +
                    "component/DefaultRootComponent.kt",
            )
        assertTrue("Исходник DefaultRootComponent не найден: ${file.absolutePath}", file.exists())
        file.readText()
    }

    @Test
    fun `the sync status repository is required, not defaulted`() {
        val declaration =
            Regex("""^\s*syncStatusRepository\s*:\s*SyncStatusRepository\s*(=[^\n]*)?,\s*$""", RegexOption.MULTILINE)
                .find(source)

        assertTrue("Параметр syncStatusRepository исчез из конструктора корня", declaration != null)
        assertTrue(
            "У syncStatusRepository не должно быть значения по умолчанию: забытая проводка обязана " +
                "не собираться, а не показывать вечное «синхронизации ещё не было». Найдено: " +
                declaration!!.value.trim(),
            declaration.groupValues[1].isEmpty(),
        )
    }

    @Test
    fun `the pending referral store is required, not defaulted`() {
        val declaration =
            Regex(
                """^\s*private\s+val\s+pendingReferralStore\s*:\s*PendingReferralStore\s*(=[^\n]*)?,\s*$""",
                RegexOption.MULTILINE,
            ).find(source)

        assertTrue("Параметр pendingReferralStore исчез из конструктора корня", declaration != null)
        assertTrue(
            "У pendingReferralStore не должно быть значения по умолчанию: умолчание-память процесса " +
                "теряет код приглашающего при смерти процесса, и ссылка молча не срабатывает. " +
                "Найдено: " + declaration!!.value.trim(),
            declaration.groupValues[1].isEmpty(),
        )
    }

    @Test
    fun `no in-file stub of the contract survives`() {
        assertTrue(
            "Заглушка SyncStatusRepository внутри модуля презентации не нужна — привязка есть в графе",
            !source.contains("emptySyncStatusRepository"),
        )
    }
}
