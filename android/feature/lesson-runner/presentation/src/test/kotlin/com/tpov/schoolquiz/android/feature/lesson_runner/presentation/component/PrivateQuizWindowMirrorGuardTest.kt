package com.tpov.schoolquiz.android.feature.lesson_runner.presentation.component

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Окно загрузки следующего вопроса (AD-31) — общая серверная константа: клиентское расписание
 * машины дублирует `NEXT_QUESTION_WINDOW_SECONDS` из `functions/private-quiz-run.js`. Ретро
 * эпика-2 flagged: немотрируемое зеркало расходится молча — этот тест читает обе стороны и
 * падает в момент расхождения, с именами констант.
 */
class PrivateQuizWindowMirrorGuardTest {

    @Test
    fun machineWindowMatchesTheServerConstant() {
        val repoRoot =
            generateSequence(File(System.getProperty("user.dir")), File::getParentFile)
                .firstOrNull { File(it, "functions/private-quiz-run.js").exists() }
                ?: error("functions/private-quiz-run.js не найден вверх по дереву")

        val serverSource = File(repoRoot, "functions/private-quiz-run.js").readText()
        val seconds =
            Regex("""NEXT_QUESTION_WINDOW_SECONDS\s*=\s*(\d+)""")
                .find(serverSource)?.groupValues?.get(1)?.toIntOrNull()
                ?: error("NEXT_QUESTION_WINDOW_SECONDS не найден на сервере")

        val machineSource =
            File(
                repoRoot,
                "android/feature/lesson-runner/presentation/src/main/kotlin" +
                    "/com/tpov/schoolquiz/android/feature/lesson_runner/presentation" +
                    "/component/PrivateQuizRunMachine.kt",
            ).readText()
        val machineMs =
            Regex("""NEXT_QUESTION_WINDOW_MS:\s*Long\s*=\s*([\d_]+)L""")
                .find(machineSource)?.groupValues?.get(1)?.replace("_", "")?.toLongOrNull()
                ?: error("NEXT_QUESTION_WINDOW_MS не найден в машине")

        assertEquals(
            seconds * 1000L,
            machineMs,
            "окно AD-31 разошлось: клиент ${machineMs}мс, сервер ${seconds}с — поменяйте обе стороны одним коммитом",
        )
    }
}
